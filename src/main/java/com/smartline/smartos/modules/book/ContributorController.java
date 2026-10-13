package com.smartline.smartos.modules.book;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.common.Paging;
import com.smartline.smartos.common.PageResponse;
import com.smartline.smartos.modules.book.BookDtos.ContributorResponse;
import com.smartline.smartos.modules.book.BookDtos.CreateContributorRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contributors")
public class ContributorController {

  private final ContributorService service;

  public ContributorController(ContributorService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<PageResponse<ContributorResponse>> list(Pageable pageable) {
    return ApiResponse.ok(PageResponse.from(
        service.list(Paging.capped(pageable)).map(BookDtos.BookMapper::toResponse)));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<ContributorResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(BookDtos.BookMapper.toResponse(service.get(id)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ORG_ADMIN','EDITOR','AUTHOR')")
  public ApiResponse<ContributorResponse> create(@Valid @RequestBody CreateContributorRequest req) {
    return ApiResponse.ok(BookDtos.BookMapper.toResponse(service.create(req)));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('ORG_ADMIN','EDITOR')")
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
