package local.mitv.launcher;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public final class PhotoUploadServerTest {
    private static int assertions;
    private static void check(boolean value) { assertions++; if (!value) throw new AssertionError("Upload assertion " + assertions); }
    private static String request(int port,String header,byte[] body) throws Exception {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(),port)) {
            socket.setSoTimeout(4000);
            socket.getOutputStream().write(header.getBytes(StandardCharsets.US_ASCII));
            if (body != null) socket.getOutputStream().write(body);
            socket.shutdownOutput();
            ByteArrayOutputStream response = new ByteArrayOutputStream(); byte[] bytes = new byte[4096]; int read;
            while ((read = socket.getInputStream().read(bytes)) != -1) response.write(bytes,0,read);
            return new String(response.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    private static String upload(int port,String code,String extras,byte[] body,int length) throws Exception {
        return request(port,"POST /upload HTTP/1.1\r\nHost: localhost\r\nX-QuietTV-Code: " + code
            + "\r\nContent-Length: " + length + "\r\n" + extras + "\r\n",body);
    }
    public static void main(String[] args) throws Exception {
        AtomicInteger saved = new AtomicInteger(); byte[] fixture = new byte[]{9,1,2,3};
        int port;
        try (PhotoUploadServer server = new PhotoUploadServer(InetAddress.getLoopbackAddress(),0,input -> {
            if (input.read() != 9) throw new java.io.IOException("invalid test image");
            int count = 1; while (input.read() != -1) count++;
            if (count != fixture.length) throw new java.io.IOException("incomplete image");
            saved.incrementAndGet();
        })) {
            port = server.port();
            check(server.code().matches("[0-9]{6}"));
            String page = request(port,"GET / HTTP/1.1\r\nHost: localhost\r\n\r\n",null);
            check(page.startsWith("HTTP/1.1 200")); check(page.contains("简净桌面") && page.contains("type='file'"));
            check(page.contains("Cache-Control: no-store") && !page.contains("Access-Control-Allow-Origin"));
            try (Socket idle = new Socket(InetAddress.getLoopbackAddress(),port)) {
                check(request(port,"GET / HTTP/1.1\r\nHost: localhost\r\n\r\n",null).startsWith("HTTP/1.1 200"));
            }
            check(upload(port,server.code(),"",fixture,fixture.length).startsWith("HTTP/1.1 200"));
            check(saved.get() == 1);
            check(upload(port,"wrong","",fixture,fixture.length).startsWith("HTTP/1.1 403"));
            check(saved.get() == 1);
            check(upload(port,server.code(),"",null,PhotoUploadServer.MAX_BYTES+1).startsWith("HTTP/1.1 413"));
            check(upload(port,server.code(),"",null,0).startsWith("HTTP/1.1 413"));
            check(upload(port,server.code(),"Transfer-Encoding: chunked\r\n",null,1).startsWith("HTTP/1.1 400"));
            check(upload(port,server.code(),"Content-Length: 2\r\n",null,2).startsWith("HTTP/1.1 400"));
            check(upload(port,server.code(),"",new byte[]{0},1).contains("invalid test image"));
            check(upload(port,server.code(),"",new byte[]{9},4).startsWith("HTTP/1.1 400"));
            check(saved.get() == 1);
            check(request(port,"GET /../../photos HTTP/1.1\r\nHost: localhost\r\n\r\n",null).startsWith("HTTP/1.1 404"));
            check(request(port,"GET /upload HTTP/1.1\r\nHost: localhost\r\n\r\n",null).startsWith("HTTP/1.1 405"));
            for (int i = 0; i < 7; i++) upload(port,"wrong","",fixture,4);
            check(upload(port,server.code(),"",fixture,4).startsWith("HTTP/1.1 429"));
        }
        try (Socket ignored = new Socket(InetAddress.getLoopbackAddress(),port)) { throw new AssertionError("Server still accepts after close"); }
        catch (java.net.ConnectException expected) { assertions++; }
        System.out.println("PASS: " + assertions + " local upload assertions");
    }
}
