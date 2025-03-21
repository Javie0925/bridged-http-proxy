package priv.jv.proxy;


import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.NettyRuntime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.context.ApplicationContext;
import priv.jv.proxy.constant.CommonConstants;
import priv.jv.proxy.handler.http.HttpProxyClientHandler;
import priv.jv.proxy.handler.socks.SocksServerInitializer;

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
public class NettyServerApplication implements CommandLineRunner {

    @Value("${netty.port:}")
    private int port;

    public static ApplicationContext context;

    public static void main(String[] args) {
        context = SpringApplication.run(NettyServerApplication.class, args);
        System.out.println();
    }

    @Override
    public void run(String... args) {
        printWelcome();
        EventLoopGroup bossGroup = new NioEventLoopGroup(CommonConstants.AVAILABLE_PROCESS);
        EventLoopGroup workerGroup = new NioEventLoopGroup(NettyRuntime.availableProcessors() * 5);
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(
                                    new HttpProxyClientHandler(),
                                    new SocksServerInitializer());
                        }
                    })
                    .bind(port)
                    .sync()
                    .channel()
                    .closeFuture()
                    .sync();
        } catch (Exception e) {
            log.error("error occurs while starting server.", e);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
        log.info("proxy server start on {} port", port);
    }

    private static void printWelcome() {
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>> starting proxy server <<<<<<<<<<<<<<<<<<<<<<");
        System.out.println(MessageFormat.format(">>>>>>>>>>>>>>>>>>>>>>>>>> available process: {0} <<<<<<<<<<<<<<<<<<<<<<",
                CommonConstants.AVAILABLE_PROCESS));
        System.out.println();
    }
}
