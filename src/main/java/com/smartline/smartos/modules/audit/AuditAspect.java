package com.smartline.smartos.modules.audit;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
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
    auditService.log(audited.action(), audited.entityType(), entityId(result, pjp.getArgs()),
        Map.of());
    return result;
  }

  /**
   * Prefers the entity's {@code getId()} over {@code toString()} (entities don't
   * override it, so the default would log useless {@code Class@hashcode} values).
   * For void operations such as delete, falls back to a UUID method argument.
   */
  private String entityId(Object result, Object[] args) {
    if (result != null) {
      try {
        Method getId = result.getClass().getMethod("getId");
        Object id = getId.invoke(result);
        if (id != null) {
          return id.toString();
        }
      } catch (Exception ignored) {
        // No accessible getId(): fall through to toString().
      }
      return result.toString();
    }
    if (args.length > 0 && args[0] instanceof UUID id) {
      return id.toString();
    }
    return null;
  }
}
