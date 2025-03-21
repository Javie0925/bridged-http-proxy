package priv.jv.proxy.handler.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 代理客户端请求目标主机处理器
 */
class HttpProxyRemoteHandler extends ChannelInboundHandlerAdapter {
    private static Logger logger = LoggerFactory.getLogger(HttpProxyRemoteHandler.class);
    private Channel serverChannel;
    private Channel remoteChannel;

    private String uuid;

    public HttpProxyRemoteHandler(Channel serverChannel, String uuid) {
        this.serverChannel = serverChannel;
        this.uuid = uuid;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.remoteChannel = ctx.channel();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        serverChannel.write(msg); // just forward
        serverChannel.writeAndFlush(uuid.getBytes()); // write uuid
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        flushAndClose(serverChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        e.printStackTrace();
        flushAndClose(remoteChannel);
    }

    private void flushAndClose(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
