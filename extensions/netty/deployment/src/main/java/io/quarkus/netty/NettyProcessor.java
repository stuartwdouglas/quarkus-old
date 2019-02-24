package io.quarkus.netty;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.substrate.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.substrate.SubstrateConfigBuildItem;

public class NettyProcessor {

    @BuildStep
    SubstrateConfigBuildItem build(BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {

        reflectiveClass.produce(new ReflectiveClassBuildItem(false, false, "io.netty.channel.socket.nio.NioSocketChannel",
                "io.netty.channel.socket.nio.NioServerSocketChannel"));
        return SubstrateConfigBuildItem.builder()
                .addNativeImageSystemProperty("io.netty.noUnsafe", "true")
                .addRuntimeReinitializedClass("io.netty.handler.codec.http2.Http2CodecUtil")
                .addRuntimeInitializedClass("io.netty.handler.codec.http.HttpObjectEncoder")
                .addRuntimeInitializedClass("io.netty.handler.codec.http2.DefaultHttp2FrameWriter")
                .addRuntimeInitializedClass("io.netty.handler.codec.http.websocketx.WebSocket00FrameEncoder")
                .addRuntimeInitializedClass("io.netty.handler.ssl.JdkNpnApplicationProtocolNegotiator")
                .addRuntimeInitializedClass("io.netty.handler.ssl.ReferenceCountedOpenSslEngine")
                .addRuntimeInitializedClass("io.netty.handler.ssl.util.ThreadLocalInsecureRandom")
                .addNativeImageSystemProperty("io.netty.leakDetection.level", "DISABLED") //TODO: make configurable
                .build();
    }

}
