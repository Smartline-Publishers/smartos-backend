package com.smartline.smartos.modules.manuscript;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.common.PageResponse;
import com.smartline.smartos.modules.manuscript.Manuscript.ManuscriptStage;
import com.smartline.smartos.modules.manuscript.ManuscriptDtos.CreateManuscriptRequest;
import com.smartline.smartos.modules.manuscript.ManuscriptDtos.ManuscriptMapper;
import com.smartline.smartos.modules.manuscript.ManuscriptDtos.ManuscriptResponse;
import com.smartline.smartos.modules.manuscript.ManuscriptDtos.TransitionManuscriptRequest;
import com.smartline.smartos.modules.manuscript.ManuscriptDtos.UpdateManuscriptRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/manuscripts")
public class ManuscriptController {

  private final ManuscriptService service;

  public ManuscriptController(ManuscriptService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<PageResponse<ManuscriptResponse>> list(
      @RequestParam(required = false) ManuscriptStage stage,
      @RequestParam(required = false) String search,
      Pageable pageable) {
    return ApiResponse.ok(PageResponse.from(
        service.list(stage, search, pageable)
            .map(m -> ManuscriptMapper.toResponse(m, List.of()))));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<ManuscriptResponse> get(@PathVariable UUID id) {
    Manuscript manuscript = service.get(id);
    return ApiResponse.ok(
        ManuscriptMapper.toResponse(manuscript, service.history(id)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ORG_ADMIN','EDITOR','AUTHOR')")
  public ApiResponse<ManuscriptResponse> create(@Valid @RequestBody CreateManuscriptRequest req) {
    return ApiResponse.ok(ManuscriptMapper.toResponse(service.create(req), List.of()));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN','EDITOR','AUTHOR')")
  public ApiResponse<ManuscriptResponse> update(@PathVariable UUID id,
      @RequestBody UpdateManuscriptRequest req) {
    return ApiResponse.ok(ManuscriptMapper.toResponse(service.update(id, req), List.of()));
  }

  @PostMapping("/{id}/transitions")
  @PreAuthorize("hasAnyRole('ORG_ADMIN','EDITOR')")
  public ApiResponse<ManuscriptResponse> transition(@PathVariable UUID id,
      @Valid @RequestBody TransitionManuscriptRequest req) {
    Manuscript manuscript = service.transition(id, req.getToStage(), req.getComment());
    return ApiResponse.ok(
        ManuscriptMapper.toResponse(manuscript, service.history(id)));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('ORG_ADMIN','EDITOR')")
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
