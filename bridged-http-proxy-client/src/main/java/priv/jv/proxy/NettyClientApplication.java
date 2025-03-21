package priv.jv.proxy;


import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.context.ApplicationContext;
import priv.jv.proxy.constant.CommonConstants;
import priv.jv.proxy.handler.http.HttpProxyClientHandler;
import priv.jv.proxy.handler.http.ServerInitiationHandler;

import java.text.MessageFormat;


/**
 * HTTP/HTTPS代理服务器
 * 三个角色：真实客户端，代理客户端，目标主机
 * <p>
 * 数据流向：
 * <p>
 * ------->> 代理客户端  --------->>
 * 真实客户端                                  目标主机
 * <<-------- 代理客户端  <<--------
 */
@Slf4j
@SpringBootApplication(exclude = {JmxAutoConfiguration.class})
public class NettyClientApplication implements CommandLineRunner {

    @Value("${netty.server.host}")
    private String host;
    @Value("${netty.server.port}")
    private int port;
    private Channel serverChannel;

    public static ApplicationContext context;

    public static void main(String[] args) {
        context = SpringApplication.run(NettyClientApplication.class, args);
        System.out.println();
    }

    private static void printWelcome() {
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>> starting proxy client <<<<<<<<<<<<<<<<<<<<<<");
        System.out.println(MessageFormat.format(">>>>>>>>>>>>>>>>>>>>>>>>>> available process: {0} <<<<<<<<<<<<<<<<<<<<<<",
                Runtime.getRuntime().availableProcessors()));
        System.out.println();
    }

    @Override
    public void run(String[] args) throws InterruptedException {
        printWelcome();
        EventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .handler(new ServerInitiationHandler())
                .handler(new HttpProxyClientHandler());
        ChannelFuture channelFuture = bootstrap.connect(host, port).sync();
        serverChannel = channelFuture.channel();
        channelFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                ByteBuf byteBuf = Unpooled.copiedBuffer(CommonConstants.ROUTE_CLIENT_FLAG.getBytes());
                serverChannel.writeAndFlush(byteBuf); // send flag
            } else {
                serverChannel.close();
                log.error("connect to server fail.");
            }
        });
        log.info("proxy server start on {} port", port);
    }
}
