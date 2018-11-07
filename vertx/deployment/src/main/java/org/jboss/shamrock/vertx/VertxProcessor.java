package org.jboss.shamrock.vertx;

import org.jboss.shamrock.annotations.BuildStep;
import org.jboss.shamrock.annotations.BuildProducer;
import org.jboss.shamrock.annotations.BuildResource;
import org.jboss.shamrock.deployment.BuildProcessingStep;
import org.jboss.shamrock.deployment.builditem.NativeImageSystemPropertyBuildItem;
import org.jboss.shamrock.deployment.builditem.ReflectiveClassBuildItem;
import org.jboss.shamrock.deployment.builditem.RuntimeInitializedClassBuildItem;

@BuildStep
class VertxProcessor implements BuildProcessingStep {

    @BuildResource
    BuildProducer<ReflectiveClassBuildItem> reflectiveClass;

    @BuildResource
    BuildProducer<RuntimeInitializedClassBuildItem> runtimeClasses;

    @BuildResource
    BuildProducer<NativeImageSystemPropertyBuildItem> nativeImage;

    @Override
    public void build() throws Exception {
    	nativeImage.produce(new NativeImageSystemPropertyBuildItem("io.netty.noUnsafe", "true"));
    	
    	runtimeClasses.produce(new RuntimeInitializedClassBuildItem("io.netty.handler.codec.http.HttpObjectEncoder"));

    			// These may need to be added depending on the application
//    			"io.netty.handler.codec.http2.Http2CodecUtil",
//    			"io.netty.handler.codec.http2.DefaultHttp2FrameWriter",
//    			"io.netty.handler.codec.http.websocketx.WebSocket00FrameEncoder"
//    			);

    	// This one may not be required after Vert.x 3.6.0 lands
    	reflectiveClass.produce(new ReflectiveClassBuildItem(false, false, "io.netty.channel.socket.nio.NioSocketChannel"));
    }

}
