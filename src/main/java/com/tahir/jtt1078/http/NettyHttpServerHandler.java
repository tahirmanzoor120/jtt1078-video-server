package com.tahir.jtt1078.http;

import com.tahir.jtt1078.entity.Media;
import com.tahir.jtt1078.publisher.PublishManager;
import com.tahir.jtt1078.server.Session;
import com.tahir.jtt1078.util.FileUtils;
import com.tahir.jtt1078.util.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyHttpServerHandler extends ChannelInboundHandlerAdapter
{
    final Logger LOGGER = LoggerFactory.getLogger(NettyHttpServerHandler.class);
    static final byte[] HTTP_403_DATA = "<h1>403 Forbidden</h1><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding--><!--padding-->".getBytes();
    static final String HEADER_ENCODING = "ISO-8859-1";

    private static final AttributeKey<Session> SESSION_KEY = AttributeKey.valueOf("session");

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception
    {
        FullHttpRequest fhr = (FullHttpRequest) msg;
        String uri = fhr.uri();
        Packet resp = Packet.create(1024);

        // The second paragraph of uri is the channel label
        if (uri.startsWith("/video/")) {
            String tag = uri.substring("/video/".length());
            resp.addBytes("HTTP/1.1 200 OK\r\n".getBytes(HEADER_ENCODING));
            resp.addBytes("Connection: keep-alive\r\n".getBytes(HEADER_ENCODING));
            resp.addBytes("Content-Type: video/x-flv\r\n".getBytes(HEADER_ENCODING));
            resp.addBytes("Transfer-Encoding: chunked\r\n".getBytes(HEADER_ENCODING));
            resp.addBytes("Cache-Control: no-cache\r\n".getBytes(HEADER_ENCODING));
            resp.addBytes("Access-Control-Allow-Origin: *\r\n".getBytes(HEADER_ENCODING));
            resp.addBytes("Access-Control-Allow-Credentials: true\r\n".getBytes(HEADER_ENCODING));
            resp.addBytes("\r\n".getBytes(HEADER_ENCODING));
            ctx.writeAndFlush(resp.getBytes()).await();

            // Subscribe to video data
            long wid = PublishManager.getInstance().subscribe(tag, Media.Type.Video, ctx).getId();
            setSession(ctx, new Session().set("subscriber-id", wid).set("tag", tag));
        } else if (uri.equals("/multimedia")) {
            responseHTMLFile("/multimedia.html", ctx);
        } else {
            ByteBuf body = Unpooled.buffer(HTTP_403_DATA.length);
            body.writeBytes(HTTP_403_DATA);
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(403), body);
            response.headers().add("Content-Length", HTTP_403_DATA.length);
            ctx.writeAndFlush(response).await();
            ctx.flush();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception
    {
        super.channelInactive(ctx);
        Session session = getSession(ctx);
        if (session != null && session.has("subscriber-id") && session.has("tag"))
        {
            String tag = session.get("tag");
            Long wid = session.get("subscriber-id");
            PublishManager.getInstance().unsubscribe(tag, wid);
        }
    }

    // Respond to static file content
    private void responseHTMLFile(String htmlFilePath, ChannelHandlerContext ctx)
    {
        byte[] fileData = FileUtils.read(NettyHttpServerHandler.class.getResourceAsStream(htmlFilePath));
        ByteBuf body = Unpooled.buffer(fileData.length);
        body.writeBytes(fileData);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(200), body);
        response.headers().add("Content-Length", fileData.length);
        ctx.write(response);
        ctx.flush();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception
    {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
    {
        ctx.close();
        cause.printStackTrace();
    }

    public final void setSession(ChannelHandlerContext context, Session session)
    {
        context.channel().attr(SESSION_KEY).set(session);
    }

    public final Session getSession(ChannelHandlerContext context)
    {
        Attribute<Session> attr = context.channel().attr(SESSION_KEY);
        if (null == attr) return null;
        else return attr.get();
    }
}

