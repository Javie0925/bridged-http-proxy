package priv.jv.proxy.handler.bean;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 真实主机的请求头信息
 */
public class HttpProxyClientHeader {
    private static Logger logger = LoggerFactory.getLogger(HttpProxyClientHeader.class);

    private String method;//请求类型
    private String host;//目标主机
    private int port;//目标主机端口
    private boolean https;//是否是https
    private boolean complete;//是否完成解析
    private ByteBuf byteBuf = Unpooled.buffer();


    private final StringBuilder lineBuf = new StringBuilder();


    public String getMethod() {
        return method;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isHttps() {
        return https;
    }

    public boolean isComplete() {
        return complete;
    }

    public ByteBuf getByteBuf() {
        return byteBuf;
    }

    /**
     * 解析header信息，建立连接
     * HTTP 请求头如下
     * GET http://www.baidu.com/ HTTP/1.1
     * Host: www.baidu.com
     * User-Agent: curl/7.69.1
     * Accept:
     *//*
         Proxy-Connection:Keep-Alive

         HTTPS请求头如下
         CONNECT www.baidu.com:443 HTTP/1.1
         Host: www.baidu.com:443
         User-Agent: curl/7.69.1
         Proxy-Connection: Keep-Alive

         * @param in
         */
    public void digest(ByteBuf in) {
        while (in.isReadable()) {
            if (complete) {
                throw new IllegalStateException("already complete");
            }
            String line = readLine(in);
            System.out.println(line);
            if (line == null) {
                return;
            }
            if (method == null) {
                method = line.split(" ")[0]; // the first word is http method name
                https = method.equalsIgnoreCase("CONNECT"); // method CONNECT means https
            }
            if (line.startsWith("Host: ")) {
                String[] arr = line.split(":");
                host = arr[1].trim();
                if (arr.length == 3) {
                    port = Integer.parseInt(arr[2]);
                } else if (https) {
                    port = 443; // https
                } else {
                    port = 80; // http
                }
            }
            if (line.isEmpty()) {
                if (host == null || port == 0) {
                    throw new IllegalStateException("cannot find header \'Host\'");
                }
                byteBuf = byteBuf.asReadOnly();
                complete = true;
                break;
            }
        }
        logger.debug(toString());
    }

    private String readLine(ByteBuf in) {
        while (in.isReadable()) {
            byte b = in.readByte();
            byteBuf.writeByte(b);
            lineBuf.append((char) b);
            int len = lineBuf.length();
            if (len >= 2 && lineBuf.substring(len - 2).equals("\r\n")) {
                String line = lineBuf.substring(0, len - 2);
                lineBuf.delete(0, len);
                return line;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "HttpProxyClientHeader{" +
                "method='" + method + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", https=" + https +
                ", complete=" + complete +
                '}';
    }
}
