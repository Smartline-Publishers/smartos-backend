package com.smartline.smartos.modules.book;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.common.Paging;
import com.smartline.smartos.common.PageResponse;
import com.smartline.smartos.modules.book.Book.BookStatus;
import com.smartline.smartos.modules.book.BookDtos.BookLinkResponse;
import com.smartline.smartos.modules.book.BookDtos.BookOptionResponse;
import com.smartline.smartos.modules.book.BookDtos.BookResponse;
import com.smartline.smartos.modules.book.BookDtos.CreateBookRequest;
import com.smartline.smartos.modules.book.BookDtos.LinkContributorRequest;
import com.smartline.smartos.modules.book.BookDtos.UpdateBookRequest;
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
@RequestMapping("/api/v1/books")
public class BookController {

  private final BookService service;

  public BookController(BookService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_books.read')")
  public ApiResponse<PageResponse<BookResponse>> list(
      @RequestParam(required = false) BookStatus status,
      @RequestParam(required = false) String search,
      Pageable pageable) {
    return ApiResponse.ok(PageResponse.from(
        service.list(status, search, Paging.capped(pageable)).map(BookDtos.BookMapper::toResponse)));
  }

  @GetMapping("/options")
  @PreAuthorize("hasAnyAuthority('PERM_books.read','PERM_orders.read','PERM_production.read',"
      + "'PERM_inventory.read','PERM_royalties.read','PERM_reports.sales.read',"
      + "'PERM_reports.editorial.read')")
  public ApiResponse<List<BookOptionResponse>> options() {
    return ApiResponse.ok(service.options().stream()
        .map(b -> new BookOptionResponse(b.getId(), b.getTitle()))
        .toList());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_books.read')")
  public ApiResponse<BookResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(
        BookDtos.BookMapper.toResponse(service.get(id), service.links(id)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERM_books.write')")
  public ApiResponse<BookResponse> create(@Valid @RequestBody CreateBookRequest req) {
    return ApiResponse.ok(BookDtos.BookMapper.toResponse(service.create(req)));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_books.write')")
  public ApiResponse<BookResponse> update(@PathVariable UUID id,
      @RequestBody UpdateBookRequest req) {
    return ApiResponse.ok(BookDtos.BookMapper.toResponse(service.update(id, req)));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_books.delete')")
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }

  @PostMapping("/{id}/contributors")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERM_books.write')")
  public ApiResponse<BookLinkResponse> linkContributor(@PathVariable UUID id,
      @Valid @RequestBody LinkContributorRequest req) {
    return ApiResponse.ok(service.linkContributor(id, req));
  }

  @DeleteMapping("/{id}/contributors/{contributorId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_books.write')")
  public void unlinkContributor(@PathVariable UUID id, @PathVariable UUID contributorId) {
    service.unlinkContributor(id, contributorId);
  }
}
