package priv.jv.proxy.handler.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.socksx.SocksVersion;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import priv.jv.proxy.config.RoutesConfig;
import priv.jv.proxy.constant.CommonConstants;
import priv.jv.proxy.handler.bean.HttpProxyClientHeader;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 代理客户端去请求目标主机
 */
@Slf4j
@Service
public class HttpProxyClientHandler extends ChannelInboundHandlerAdapter {

    @Resource
    private RoutesConfig routesConfig;

    /*代理客户端channel*/
    private static Channel routeChannel;
    private Channel clientChannel;
    /*目标主机channel*/
    private Channel remoteChannel;
    /*解析真实客户端的header*/
    private HttpProxyClientHeader header = new HttpProxyClientHeader();

    private Map<String, Channel> uuidChannelMap = new HashMap<>();

    public HttpProxyClientHandler() {
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info(ctx.toString());
    }

    /**
     * 注意在真实客户端请求一个页面的时候，此方法不止调用一次，
     * 这是TCP底层决定的（拆包/粘包）
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf in = (ByteBuf) msg;
        if (isRouteClientConnection(in)) {
            routeChannel = ctx.channel();
            clientChannel.config().setAutoRead(true);
            log.info("Route client is ready. remote address:{}", routeChannel.remoteAddress());
            return;
        }
        if (isRouteClient(ctx.channel())) {
            dealRoutChannelRead(ctx, msg);
            return;
        }
        clientChannel = ctx.channel();
        int readerIndex = in.readerIndex();
        // skip socks request
        if (in.writerIndex() != readerIndex) {
            ChannelPipeline p = ctx.pipeline();
            byte versionVal = in.getByte(readerIndex);
            SocksVersion version = SocksVersion.valueOf(versionVal);
            if (SocksVersion.SOCKS4a.byteValue() == version.byteValue()
                    || SocksVersion.SOCKS5.byteValue() == version.byteValue()) {
                super.channelRead(ctx, msg);
                p.remove(this);
                return;
            }
        }

        if (header.isComplete()) {
            /*
            如果在真实客户端的本次请求中已经解析过header了，
            说明代理客户端已经在目标主机建立了连接，直接将真实客户端的数据写给目标主机
            */
            if (shouldRoute(header.getHost())) {
                routeChannel.writeAndFlush(msg); // just forward
                return;
            } else {
                remoteChannel.writeAndFlush(msg);
            }
        }

        HttpProxyClientHeader header = new HttpProxyClientHeader();

        header.digest(in);/*解析目标主机信息*/

        if (!header.isComplete()) {
            /*如果解析过一次header之后未完成解析，直接返回，释放buf*/
            in.release();
            return;
        }

        // disable AutoRead until remote connection is ready
        clientChannel.config().setAutoRead(false);

        if (header.isHttps()) { // if https, respond 200 to create tunnel
            clientChannel.writeAndFlush(Unpooled.wrappedBuffer("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes()));
        }

        if (shouldRoute(header.getHost())) {
            uuidChannelMap.put(UUID.randomUUID().toString(), clientChannel);
            return;
        }

        /**
         *
         * 下面为真实客户端第一次来到的时候，代理客户端向目标客户端发起连接
         */
        Bootstrap b = new Bootstrap();
        b.group(clientChannel.eventLoop()) // use the same EventLoop
                .channel(clientChannel.getClass())
                .handler(new HttpProxyRemoteHandler(clientChannel));
        ChannelFuture f = b.connect(header.getHost(), header.getPort());
        remoteChannel = f.channel();
        f.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                // connection is ready, enable AutoRead
                clientChannel.config().setAutoRead(true);
                // forward header and remaining bytes
                if (!header.isHttps()) {
                    // in读取一次缓冲区就没有了，header.byteBuf里面存了一份
                    remoteChannel.writeAndFlush(header.getByteBuf());
                }
            } else {
                in.release();
                clientChannel.close();
            }
        });
    }

    private boolean shouldRoute(String host) {
        return Objects.nonNull(routeChannel) && routeChannel.isOpen() && routesConfig.getRouteHosts().contains(host);
    }

    private void dealRoutChannelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf in = (ByteBuf) msg;
        byte[] uuidBytes = new byte[32];
        in.readBytes(uuidBytes, in.readableBytes() - 32, in.readableBytes());
        String uuid = new String(uuidBytes);
        Channel channel = uuidChannelMap.get(uuid);
        channel.writeAndFlush(in.copy(0, in.readableBytes() - 32));
    }

    private boolean isRouteClient(Channel channel) {
        return Objects.equals(channel, routeChannel);
    }

    private boolean isRouteClientConnection(ByteBuf in) {
        byte[] flagBytes = new byte[CommonConstants.ROUTE_CLIENT_FLAG.length()];
        in.readBytes(flagBytes);
        return Objects.equals(new String(flagBytes), CommonConstants.ROUTE_CLIENT_FLAG);
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        flushAndClose(clientChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        log.error(e.toString(), e);
        flushAndClose(routeChannel);
    }

    private void flushAndClose(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
