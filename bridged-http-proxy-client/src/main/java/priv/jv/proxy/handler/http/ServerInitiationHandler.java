package priv.jv.proxy.handler.http;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import priv.jv.proxy.constant.CommonConstants;

public class ServerInitiationHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        channel.writeAndFlush(CommonConstants.ROUTE_CLIENT_FLAG);
    }
}
