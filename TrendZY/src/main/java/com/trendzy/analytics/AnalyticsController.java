package com.trendzy.analytics;

import com.trendzy.security.User;
import com.trendzy.security.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.Instant;
import java.util.Optional;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final ProductClickRepository productClickRepository;
    private final UserRepository userRepository;

    public AnalyticsController(ProductClickRepository productClickRepository, UserRepository userRepository) {
        this.productClickRepository = productClickRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/click")
    public ResponseEntity<Void> trackClick(@RequestBody ClickRequest request, Principal principal) {
        String userId = null;
        if (principal != null && principal.getName() != null) {
            String email = principal.getName();
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                userId = userOpt.get().getId();
            } else {
                userId = email; // Fallback
            }
        }

        ProductClick click = new ProductClick(
                userId,
                request.getTrendId(),
                request.getSource(),
                request.getUrl(),
                Instant.now()
        );

        productClickRepository.save(click);

        return ResponseEntity.ok().build();
    }
}
