package com.smartline.smartos.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Pagination guardrails. List endpoints cap client page sizes so a single
 * request cannot force unbounded result windows (roadmap: size ≤ 100).
 */
public final class Paging {

  /** Maximum page size accepted by list endpoints. */
  public static final int MAX_SIZE = 100;

  private Paging() {
  }

  public static Pageable capped(Pageable pageable) {
    int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_SIZE);
    return PageRequest.of(Math.max(pageable.getPageNumber(), 0), size, pageable.getSort());
  }

  public static Pageable capped(Pageable pageable, Sort defaultSort) {
    Pageable base = pageable.getSort().isSorted() ? pageable
        : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
    return capped(base);
  }
}
