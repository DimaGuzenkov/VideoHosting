package com.example.processor.ffmpeg;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class HlsPlaylistBuilder {

    public void writeMasterPlaylist(Path outputDir, List<Quality> qualities) throws IOException {
        StringBuilder sb = new StringBuilder("#EXTM3U\n");
        for (Quality q : qualities) {
            sb.append(String.format(
                    "#EXT-X-STREAM-INF:BANDWIDTH=%d,RESOLUTION=%dx%d\nplaylist_%s.m3u8\n",
                    q.bandwidth(), q.width(), q.height(), q.name()
            ));
        }
        Files.write(outputDir.resolve("master.m3u8"), sb.toString().getBytes());
    }
}