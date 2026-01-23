package com.maintenance_match.matching.service;

import com.maintenance_match.matching.dto.MaintainerProfileEvent;
import com.maintenance_match.matching.model.Maintainer;
import com.maintenance_match.matching.repository.MaintainerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaintainerEventListener {

    private final MaintainerRepository maintainerRepository;

    @KafkaListener(topics = "${app.kafka.topics.maintainer-profile-creation}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onMaintainerApproved(MaintainerProfileEvent event) {
        log.info("Received request to create profile for user {}", event.getUserId());

        if (maintainerRepository.findByUserId(event.getUserId()).isPresent()) {
            log.info("Profile already exists for user {}. Skipping.", event.getUserId());
            return;
        }

        Maintainer newProfile = Maintainer.builder()
                .userId(event.getUserId())
                .name(event.getName())
                .isAvailable(true)
                .build(); // Capacity and activeJobs default to 1 and 0

        maintainerRepository.save(newProfile);
        log.info("Created maintainer profile for {}", event.getName());
    }
}
