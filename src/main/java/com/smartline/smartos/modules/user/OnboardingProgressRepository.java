package com.smartline.smartos.modules.user;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingProgressRepository extends JpaRepository<OnboardingProgress, UUID> {
}
