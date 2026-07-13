package com.trendzy.ingestion.controller;

import com.trendzy.ingestion.model.ArchivedTrend;
import com.trendzy.ingestion.model.Trend;
import com.trendzy.ingestion.repository.ArchivedTrendRepository;
import com.trendzy.ingestion.repository.TrendRepository;
import com.trendzy.security.User;
import com.trendzy.security.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v2/archive/trends")
@RequiredArgsConstructor
@Slf4j
public class ArchiveController {

    private final ArchivedTrendRepository archivedTrendRepository;
    private final TrendRepository trendRepository;
    private final UserRepository userRepository;

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
        
        String email = (String) authentication.getPrincipal();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @GetMapping
    public ResponseEntity<List<ArchivedTrend>> getArchivedTrends() {
        User user = getAuthenticatedUser();
        List<ArchivedTrend> archived = archivedTrendRepository.findByUserIdOrderByArchivedAtDesc(user.getId());
        return ResponseEntity.ok(archived);
    }

    @PostMapping("/{trendId}")
    public ResponseEntity<ArchivedTrend> archiveTrend(@PathVariable String trendId) {
        User user = getAuthenticatedUser();
        
        // Check if already archived
        Optional<ArchivedTrend> existing = archivedTrendRepository.findByUserIdAndOriginalTrendId(user.getId(), trendId);
        if (existing.isPresent()) {
            return ResponseEntity.ok(existing.get()); // Already archived
        }
        
        // Find the original trend to snapshot it
        Trend trend = trendRepository.findById(trendId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trend not found"));
                
        ArchivedTrend archivedTrend = ArchivedTrend.builder()
                .userId(user.getId())
                .originalTrendId(trend.getId())
                .trendSnapshot(trend)
                .archivedAt(LocalDateTime.now())
                .build();
                
        ArchivedTrend saved = archivedTrendRepository.save(archivedTrend);
        log.info("[ARCHIVE] User {} archived trend {}", user.getId(), trendId);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{trendId}")
    public ResponseEntity<Void> unarchiveTrend(@PathVariable String trendId) {
        User user = getAuthenticatedUser();
        
        archivedTrendRepository.deleteByUserIdAndOriginalTrendId(user.getId(), trendId);
        log.info("[ARCHIVE] User {} unarchived trend {}", user.getId(), trendId);
        
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{trendId}/status")
    public ResponseEntity<Boolean> getArchiveStatus(@PathVariable String trendId) {
        try {
            User user = getAuthenticatedUser();
            boolean isArchived = archivedTrendRepository.findByUserIdAndOriginalTrendId(user.getId(), trendId).isPresent();
            return ResponseEntity.ok(isArchived);
        } catch (Exception e) {
            return ResponseEntity.ok(false);
        }
    }
}
