package com.example.processor.ffmpeg;

import com.example.processor.processing.VideoProcessingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FfmpegService {

    private final VideoProcessingProperties props;

    private static final String SHELL;
    private static final String SHELL_ARG;

    static {
        boolean win = System.getProperty("os.name").toLowerCase().contains("win");
        SHELL = win ? "cmd" : "sh";
        SHELL_ARG = win ? "/c" : "-c";
    }

    public void convertToHls(Path input, Path outputDir, List<Quality> qualities) {
        if (qualities.isEmpty()) {
            throw new IllegalArgumentException("No qualities configured");
        }

        String cmd = buildCommand(input, outputDir, qualities);
        log.info("🎬 Running ffmpeg for {} qualities: {}",
                qualities.size(),
                qualities.stream().map(Quality::name).collect(Collectors.joining(", ")));

        long start = System.currentTimeMillis();
        int exit = execute(cmd);
        long elapsed = (System.currentTimeMillis() - start) / 1000;

        if (exit != 0) {
            throw new RuntimeException("FFmpeg failed with exit code " + exit
                    + " after " + elapsed + "s");
        }
        log.info("✅ FFmpeg completed in {}s", elapsed);
    }

    private String buildCommand(Path input, Path outputDir, List<Quality> qualities) {
        StringBuilder cmd = new StringBuilder("ffmpeg -y -hide_banner -i ");
        cmd.append(input);

        // ---- filter_complex ----
        cmd.append(" -filter_complex \"");
        cmd.append("[0:v]split=").append(qualities.size());
        for (int i = 0; i < qualities.size(); i++) {
            cmd.append("[v").append(i).append("]");
        }
        cmd.append(";");

        for (int i = 0; i < qualities.size(); i++) {
            Quality q = qualities.get(i);
            cmd.append("[v").append(i).append("]scale=")
                    .append(q.width()).append(":").append(q.height())
                    .append(":force_original_aspect_ratio=decrease:force_divisible_by=2")
                    .append("[out").append(i).append("]");
            if (i < qualities.size() - 1) cmd.append(";");
        }
        cmd.append("\"");

        // ---- outputs ----
        for (int i = 0; i < qualities.size(); i++) {
            Quality q = qualities.get(i);
            cmd.append(" -map \"[out").append(i).append("]\"")
                    .append(" -map 0:a?")
                    .append(" -c:v libx264 -preset ultrafast -c:a aac")
                    .append(" -hls_time ").append(props.getHlsTime())
                    .append(" -hls_list_size ").append(props.getHlsListSize())
                    .append(" -hls_segment_filename ").append(outputDir).append("/segment_")
                    .append(q.name()).append("_%03d.ts")
                    .append(" -f hls ")
                    .append(outputDir.resolve("playlist_" + q.name() + ".m3u8"));
        }

        return cmd.toString();
    }

    private int execute(String cmd) {
        log.debug("FFmpeg command: {}", cmd);
        try {
            ProcessBuilder pb = new ProcessBuilder(SHELL, SHELL_ARG, cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            return process.waitFor();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to run ffmpeg: " + e.getMessage(), e);
        }
    }
}