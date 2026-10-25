package local.mitv.launcher;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Foreground-only LAN photo receiver. No file-serving or filesystem-path API. */
final class PhotoUploadServer implements java.io.Closeable {
    interface Receiver { void save(InputStream bytes) throws Exception; }
    static final int MAX_BYTES = 32 * 1024 * 1024;
    private final ServerSocket listener;
    private final Receiver receiver;
    private final Thread thread;
    private final String code = String.format(Locale.ROOT,"%06d",new SecureRandom().nextInt(1_000_000));
    private volatile boolean closed;
    private final Set<Socket> active = Collections.synchronizedSet(new HashSet<Socket>());
    private final AtomicInteger rejected = new AtomicInteger();
    private final ThreadPoolExecutor transfers = new ThreadPoolExecutor(3,3,0,TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(3),r -> { Thread t = new Thread(r,"QuietTV-photo-transfer"); t.setDaemon(true); return t; });

    PhotoUploadServer(InetAddress address, int port, Receiver receiver) throws IOException {
        this.receiver = receiver;
        listener = new ServerSocket(port, 2, address);
        thread = new Thread(this::accept,"QuietTV-photo-upload"); thread.setDaemon(true); thread.start();
    }
    int port() { return listener.getLocalPort(); }
    String code() { return code; }
    String url() { return "http://"+listener.getInetAddress().getHostAddress()+":"+port()+"/"; }
    String scanUrl() { return url()+"#code="+code; }

    private void accept() {
        while (!closed) {
            try {
                Socket socket = listener.accept();
                if (closed) { socket.close(); break; }
                active.add(socket);
                try { transfers.execute(() -> transfer(socket)); }
                catch (java.util.concurrent.RejectedExecutionException full) { active.remove(socket); socket.close(); }
            } catch (IOException ignored) { /* Closing the page interrupts accept and any active transfer. */ }
        }
    }

    private void transfer(Socket socket) {
        try (Socket connection = socket) {
            // Browser pre-connections must not block another browser's upload.
            connection.setSoTimeout(5000);
            try { serve(connection); }
            catch (java.net.SocketTimeoutException idle) { /* No request: silently close a browser pre-connection. */ }
            catch (Exception error) { reply(connection.getOutputStream(),400,"text/plain", "图片未保存，请重试"); }
        } catch (IOException ignored) {}
        finally { active.remove(socket); }
    }

    private void serve(Socket socket) throws Exception {
        InputStream input = socket.getInputStream(); OutputStream output = socket.getOutputStream();
        ByteArrayOutputStream header = new ByteArrayOutputStream(); int matched = 0;
        while (matched < 4) {
            int value = input.read();
            if (value == -1) return;
            header.write(value);
            if (header.size() > 8192) { reply(output,431,"text/plain","请求头过大"); return; }
            int expected = (matched == 0 || matched == 2) ? '\r' : '\n';
            matched = value == expected ? matched + 1 : value == '\r' ? 1 : 0;
        }
        String[] lines = new String(header.toByteArray(),StandardCharsets.ISO_8859_1).split("\r\n");
        String[] request = lines[0].split(" ");
        if (request.length != 3) { reply(output,400,"text/plain","请求无效"); return; }
        Map<String,String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int separator = lines[i].indexOf(':');
            if (separator <= 0) continue;
            String name = lines[i].substring(0,separator).toLowerCase(Locale.ROOT);
            if (headers.put(name,lines[i].substring(separator+1).trim()) != null) {
                reply(output,400,"text/plain","重复请求头"); return;
            }
        }
        if (request[0].equals("GET") && request[1].equals("/")) {
            reply(output,200,"text/html",page()); return;
        }
        if (!request[1].equals("/upload")) { reply(output,404,"text/plain","没有这个页面"); return; }
        if (!request[0].equals("POST")) { reply(output,405,"text/plain","请选择图片上传"); return; }
        if (headers.containsKey("transfer-encoding")) { reply(output,400,"text/plain","请使用页面上的选择图片按钮"); return; }
        long length;
        try { length = Long.parseLong(value(headers,"content-length")); }
        catch (NumberFormatException error) { length = -1; }
        if (length <= 0 || length > MAX_BYTES) { reply(output,413,"text/plain","单张图片须小于32MB，且不能为空"); return; }
        Body body = new Body(input,length);
        if (rejected.get() >= 8) { discard(body); reply(output,429,"text/plain","连接码错误次数过多，请在电视关闭后重新打开传图"); return; }
        if (!java.security.MessageDigest.isEqual(code.getBytes(StandardCharsets.US_ASCII),
                value(headers,"x-quiettv-code").getBytes(StandardCharsets.US_ASCII))) {
            rejected.incrementAndGet(); discard(body);
            reply(output,403,"text/plain","连接码不正确，请查看电视上的六位数字"); return;
        }
        try {
            socket.setSoTimeout(30_000);
            receiver.save(body);
            reply(output,200,"text/plain","已保存到电视屏保");
        } catch (Exception error) {
            // Android resets a socket closed with unread incoming bytes, which can erase the
            // error response. Drain this bounded request so browsers can read the useful message.
            discard(body);
            String message = error.getMessage();
            reply(output,400,"text/plain", message == null ? "这张图片无法保存" : message.substring(0,Math.min(message.length(),160)));
        }
    }

    private static void discard(InputStream input) {
        try { byte[] bytes = new byte[8192]; while (input.read(bytes) != -1) {} }
        catch (IOException ignored) { /* A disconnected sender has nothing left to display. */ }
    }

    private static String value(Map<String,String> headers,String key) {
        String value = headers.get(key); return value == null ? "" : value;
    }

    private static final class Body extends FilterInputStream {
        private long remaining;
        Body(InputStream input,long length) { super(input); remaining = length; }
        @Override public int read() throws IOException {
            if (remaining == 0) return -1;
            int value = in.read(); if (value == -1) throw new EOFException("图片传输中断"); remaining--; return value;
        }
        @Override public int read(byte[] bytes,int offset,int count) throws IOException {
            if (count == 0) return 0;
            if (remaining == 0) return -1;
            int read = in.read(bytes,offset,(int)Math.min(count,remaining));
            if (read == -1) throw new EOFException("图片传输中断"); remaining -= read; return read;
        }
    }

    private static void reply(OutputStream output,int status,String type,String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 " + status + (status == 200 ? " OK" : " Error") + "\r\nContent-Type: " + type
            + "; charset=utf-8\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\nCache-Control: no-store"
            + "\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nX-Frame-Options: DENY\r\n\r\n";
        output.write(header.getBytes(StandardCharsets.US_ASCII)); output.write(bytes); output.flush();
    }

    private static String page() {
        return "<!doctype html><html lang='zh-CN'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<title>简净桌面 · 屏保传图</title><style>body{max-width:640px;margin:48px auto;padding:0 24px;background:#1a1c20;color:#eee;font:18px/1.7 sans-serif}h1{font-size:28px}input,button{font:inherit;padding:12px;margin:8px 0;border-radius:8px;max-width:100%;box-sizing:border-box}button{background:#63debd;border:0}pre{white-space:pre-wrap;font:inherit;color:#b3c4be}</style>"
            + "<h1>把照片加入电视屏保</h1><p id='intro'>请输入电视上显示的连接码，然后选择照片。</p><p>支持 JPG、PNG、WebP、BMP，最多24张，单张32MB。</p>"
            + "<label>连接码 <input id='code' type='password' inputmode='numeric' maxlength='6' autocomplete='off' placeholder='六位数字'></label><br>"
            + "<input id='files' type='file' accept='image/jpeg,image/png,image/webp,image/bmp' multiple><br>"
            + "<button id='send'>保存到电视屏保</button><pre id='result' role='status'></pre><p>传输仅在电视的传图页面打开时可用。照片保存在电视内，电脑和手机的原图保留。</p>"
            + "<script>function connectFromScan(){var m=/^#code=([0-9]{6})$/.exec(location.hash);if(!m)return;"
            + "document.getElementById('code').value=m[1];document.getElementById('intro').textContent='连接码已填好，请选择照片，然后保存到电视屏保。';"
            + "try{history.replaceState(null,'',location.pathname+location.search);}catch(e){}}"
            + "connectFromScan();window.addEventListener('hashchange',connectFromScan);"
            + "document.getElementById('send').onclick=async function(){var b=this,r=document.getElementById('result'),c=document.getElementById('code').value,f=document.getElementById('files').files;"
            + "if(!/^\\d{6}$/.test(c)||!f.length){r.textContent='请填写六位连接码并选择照片';return;}b.disabled=true;r.textContent='';"
            + "try{for(var i=0;i<f.length;i++){var x=f[i];r.textContent+='['+(i+1)+'/'+f.length+'] '+x.name+'：';"
            + "if(x.size>33554432||!x.size){r.textContent+='跳过，文件为空或超过32MB\\n';continue;}"
            + "var q=await fetch('/upload',{method:'POST',headers:{'Content-Type':'application/octet-stream','X-QuietTV-Code':c},body:x});"
            + "r.textContent+=(await q.text())+'\\n';if(q.status===403||q.status===429)break;}}catch(e){r.textContent+='连接中断，请确认电视仍停留在传图页面';}finally{b.disabled=false;}}</script></html>";
    }

    @Override public void close() {
        closed = true;
        try { listener.close(); } catch (IOException ignored) {}
        synchronized (active) {
            for (Socket socket : active) try { socket.close(); } catch (IOException ignored) {}
        }
        transfers.shutdownNow();
        thread.interrupt();
    }
}
