package com.smartline.smartos.tenant;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcTenantConfig implements WebMvcConfigurer {

  private final TenantInterceptor tenantInterceptor;

  public WebMvcTenantConfig(TenantInterceptor tenantInterceptor) {
    this.tenantInterceptor = tenantInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(tenantInterceptor).addPathPatterns("/api/**");
  }
}
