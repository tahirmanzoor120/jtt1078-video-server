package com.tahir.jtt1078.subscriber;

import com.tahir.jtt1078.util.*;
import com.tahir.jtt1078.util.Configs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

public class RTMPPublisher extends Thread
{
    static Logger logger = LoggerFactory.getLogger(RTMPPublisher.class);

    String tag;
    Process process = null;

    public RTMPPublisher(String tag)
    {
        this.tag = tag;
    }

    @Override
    public void run()
    {
        InputStream stderr;
        int len = -1;
        byte[] buff = new byte[512];
        boolean debugMode = Configs.getBoolean("debug.mode");

        try {
            String rtmpUrl = Configs.get("rtmp.url").replaceAll("\\{TAG\\}", tag);
            String cmd = String.format("%s -i http://localhost:%d/video/%s -vcodec copy -acodec aac -f flv %s",
                        Configs.get("ffmpeg.path"),
                        Configs.getInt("server.http.port", 3333),
                        tag,
                        rtmpUrl
                    );

            logger.info("Executing command: {}", cmd);
            process = Runtime.getRuntime().exec(cmd);

            stderr = process.getErrorStream();
            while ((len = stderr.read(buff)) > -1) {
                if (debugMode) System.out.print(new String(buff, 0, len));
            }

            logger.info("Video published");
        }
        catch(Exception ex)
        {
            logger.error("RTMP publishing failed", ex);
        }
    }

    public void close()
    {
        try { if (process != null) process.destroyForcibly(); } catch(Exception e) { }
    }
}