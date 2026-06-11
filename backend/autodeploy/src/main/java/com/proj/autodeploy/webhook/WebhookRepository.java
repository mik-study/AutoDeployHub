package com.proj.autodeploy.webhook;

import com.proj.autodeploy.webhook.domain.Webhook;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookRepository extends JpaRepository<Webhook, Long> {

    Optional<Webhook> findByDeliveryId(String deliveryId);

    boolean existsByDeliveryId(String deliveryId);
}
