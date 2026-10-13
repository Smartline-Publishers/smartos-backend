package com.smartline.smartos.modules.audit;

import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {

  private final AuditService auditService;

  public AuditAspect(AuditService auditService) {
    this.auditService = auditService;
  }

  @Around("@annotation(audited)")
  public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
    Object result = pjp.proceed();
    String entityId = result != null ? result.toString() : null;
    auditService.log(audited.action(), audited.entityType(), entityId, Map.of());
    return result;
  }
}
