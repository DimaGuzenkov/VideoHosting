//package com.example.processor.processing;
//
//import com.example.processor.ffmpeg.Quality;
//import com.example.processor.db.ProcessingTaskRepository;
//import com.example.processor.scheduler.data.TaskStatus;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//
//@Service
//@RequiredArgsConstructor
//public class ProcessingTaskService {
//
//    private final ProcessingTaskRepository taskRepo;
//
//    @Transactional
//    public List<Quality> claim(Long videoId, List<Quality> qualities) {
//        List<Quality> claimed = new ArrayList<>();
//        for (Quality q : qualities) {
//            if (taskRepo.claim(videoId, q.name(), LocalDateTime.now()) == 1) {
//                claimed.add(q);
//            }
//        }
//        return claimed;
//    }
//
//    @Transactional
//    public void markDone(Long videoId, List<Quality> qualities) {
//        updateStatus(videoId, qualities, TaskStatus.DONE);
//    }
//
//    @Transactional
//    public void markFailed(Long videoId, List<Quality> qualities) {
//        updateStatus(videoId, qualities, TaskStatus.FAILED);
//    }
//
//    @Transactional
//    public void markCanceled(Long videoId, List<Quality> qualities) {
//        updateStatus(videoId, qualities, TaskStatus.CANCELLED);
//    }
//
//    private void updateStatus(Long videoId, List<Quality> qualities, TaskStatus status) {
//        for (Quality q : qualities) {
//            taskRepo.findByVideoIdAndQuality(videoId, q.name()).ifPresent(t -> {
//                t.setStatus(status);
//                t.setFinishedAt(LocalDateTime.now());
//            });
//        }
//    }
//}
