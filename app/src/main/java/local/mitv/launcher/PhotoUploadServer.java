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
    enum Message { PAGE, INTERRUPTED, PHOTO_NOT_SAVED_PLEASE_TRY, REQUEST_HEADERS_TOO_LARGE, INVALID_REQUEST, DUPLICATE_REQUEST_HEADER, PAGE_NOT_FOUND, CHOOSE_A_PHOTO_TO_UPLOAD, USE_THE_PHOTO_PICKER_ON, EACH_PHOTO_MUST_BE_NON, TOO_MANY_INCORRECT_CODES_CLOSE, INCORRECT_CODE, SAVED_TO_TV_SCREENSAVER, COULD_NOT_SAVE_THIS_PHOTO }
    interface Labels { String text(Message value); }
    interface Receiver { void save(InputStream bytes) throws Exception; }
    static final int MAX_BYTES = 32 * 1024 * 1024;
    private final ServerSocket listener;
    private final Receiver receiver;
    private final Labels labels;
    private final Thread thread;
    private final String code = String.format(Locale.ROOT,"%06d",new SecureRandom().nextInt(1_000_000));
    private volatile boolean closed;
    private final Set<Socket> active = Collections.synchronizedSet(new HashSet<Socket>());
    private final AtomicInteger rejected = new AtomicInteger();
    private final ThreadPoolExecutor transfers = new ThreadPoolExecutor(3,3,0,TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(3),r -> { Thread t = new Thread(r,"QuietTV-photo-transfer"); t.setDaemon(true); return t; });

    PhotoUploadServer(InetAddress address, int port, Receiver receiver, Labels labels) throws IOException {
        this.labels = labels;
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
            catch (Exception error) { reply(connection.getOutputStream(),400,"text/plain", labels.text(Message.PHOTO_NOT_SAVED_PLEASE_TRY)); }
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
            if (header.size() > 8192) { reply(output,431,"text/plain",labels.text(Message.REQUEST_HEADERS_TOO_LARGE)); return; }
            int expected = (matched == 0 || matched == 2) ? '\r' : '\n';
            matched = value == expected ? matched + 1 : value == '\r' ? 1 : 0;
        }
        String[] lines = new String(header.toByteArray(),StandardCharsets.ISO_8859_1).split("\r\n");
        String[] request = lines[0].split(" ");
        if (request.length != 3) { reply(output,400,"text/plain",labels.text(Message.INVALID_REQUEST)); return; }
        Map<String,String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int separator = lines[i].indexOf(':');
            if (separator <= 0) continue;
            String name = lines[i].substring(0,separator).toLowerCase(Locale.ROOT);
            if (headers.put(name,lines[i].substring(separator+1).trim()) != null) {
                reply(output,400,"text/plain",labels.text(Message.DUPLICATE_REQUEST_HEADER)); return;
            }
        }
        if (request[0].equals("GET") && request[1].equals("/")) {
            reply(output,200,"text/html",labels.text(Message.PAGE)); return;
        }
        if (!request[1].equals("/upload")) { reply(output,404,"text/plain",labels.text(Message.PAGE_NOT_FOUND)); return; }
        if (!request[0].equals("POST")) { reply(output,405,"text/plain",labels.text(Message.CHOOSE_A_PHOTO_TO_UPLOAD)); return; }
        if (headers.containsKey("transfer-encoding")) { reply(output,400,"text/plain",labels.text(Message.USE_THE_PHOTO_PICKER_ON)); return; }
        long length;
        try { length = Long.parseLong(value(headers,"content-length")); }
        catch (NumberFormatException error) { length = -1; }
        if (length <= 0 || length > MAX_BYTES) { reply(output,413,"text/plain",labels.text(Message.EACH_PHOTO_MUST_BE_NON)); return; }
        Body body = new Body(input,length);
        if (rejected.get() >= 8) { discard(body); reply(output,429,"text/plain",labels.text(Message.TOO_MANY_INCORRECT_CODES_CLOSE)); return; }
        if (!java.security.MessageDigest.isEqual(code.getBytes(StandardCharsets.US_ASCII),
                value(headers,"x-quiettv-code").getBytes(StandardCharsets.US_ASCII))) {
            rejected.incrementAndGet(); discard(body);
            reply(output,403,"text/plain",labels.text(Message.INCORRECT_CODE)); return;
        }
        try {
            socket.setSoTimeout(30_000);
            receiver.save(body);
            reply(output,200,"text/plain",labels.text(Message.SAVED_TO_TV_SCREENSAVER));
        } catch (Exception error) {
            // Android resets a socket closed with unread incoming bytes, which can erase the
            // error response. Drain this bounded request so browsers can read the useful message.
            discard(body);
            String message = error instanceof EOFException ? labels.text(Message.INTERRUPTED) : error.getMessage();
            reply(output,400,"text/plain", message == null ? labels.text(Message.COULD_NOT_SAVE_THIS_PHOTO) : message.substring(0,Math.min(message.length(),160)));
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
            int value = in.read(); if (value == -1) throw new EOFException("Incomplete upload body"); remaining--; return value;
        }
        @Override public int read(byte[] bytes,int offset,int count) throws IOException {
            if (count == 0) return 0;
            if (remaining == 0) return -1;
            int read = in.read(bytes,offset,(int)Math.min(count,remaining));
            if (read == -1) throw new EOFException("Incomplete upload body"); remaining -= read; return read;
        }
    }

    private static void reply(OutputStream output,int status,String type,String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 " + status + (status == 200 ? " OK" : " Error") + "\r\nContent-Type: " + type
            + "; charset=utf-8\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\nCache-Control: no-store"
            + "\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nX-Frame-Options: DENY\r\n\r\n";
        output.write(header.getBytes(StandardCharsets.US_ASCII)); output.write(bytes); output.flush();
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
