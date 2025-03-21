package priv.jv.proxy.handler.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.socksx.SocksVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import priv.jv.proxy.handler.bean.HttpProxyClientHeader;

import java.util.HashMap;
import java.util.Map;

/**
 * 代理客户端去请求目标主机
 */
public class HttpProxyClientHandler extends ChannelInboundHandlerAdapter {
    private static Logger logger = LoggerFactory.getLogger(HttpProxyRemoteHandler.class);
    /*代理服务端channel*/
    private Channel serverChannel;
    /*解析真实客户端的header*/
    private Map<String, Channel> uuidChannelMap = new HashMap<>();


    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        serverChannel = ctx.channel();
    }

    /**
     * 注意在真实客户端请求一个页面的时候，此方法不止调用一次，
     * 这是TCP底层决定的（拆包/粘包）
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf in = (ByteBuf) msg;
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

        HttpProxyClientHeader header = new HttpProxyClientHeader();

        header.digest(in);/*解析目标主机信息*/


        if (!header.isComplete()) {
            /*如果解析过一次header之后未完成解析，直接返回，释放buf*/
            in.release();
            return;
        }

        String uuid = readUUID(in);
        if (uuidChannelMap.containsKey(uuid)) {
            Channel remoteChannel = uuidChannelMap.get(uuid);
            remoteChannel.writeAndFlush(in.copy(0,in.readableBytes()-32)); // 去除尾部uuid
        }

        Bootstrap b = new Bootstrap();
        b.group(serverChannel.eventLoop()) // use the same EventLoop
                .channel(serverChannel.getClass())
                .handler(new HttpProxyRemoteHandler(serverChannel, uuid));
        ChannelFuture f = b.connect(header.getHost(), header.getPort());
        Channel remoteChannel = f.channel();
        uuidChannelMap.put(uuid, remoteChannel);
        f.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                // forward header and remaining bytes
                if (!header.isHttps()) {
                    // in读取一次缓冲区就没有了，header.byteBuf里面存了一份
                    remoteChannel.writeAndFlush(header.getByteBuf());
                }
            } else {
                in.release();
                serverChannel.close();
            }
        });
    }

    private String readUUID(ByteBuf in) {
        byte[] bytes = new byte[32];
        in.readBytes(bytes, in.readableBytes() - 32, 32);
        return new String(bytes);
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        flushAndClose(serverChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        logger.error(e.toString(), e);
        flushAndClose(serverChannel);
    }

    private void flushAndClose(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
