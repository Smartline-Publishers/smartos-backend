package com.smartline.smartos.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PagingTest {

  @Test
  void oversizedPagesAreCappedAt100() {
    assertEquals(100, Paging.capped(PageRequest.of(2, 500)).getPageSize());
    assertEquals(2, Paging.capped(PageRequest.of(2, 500)).getPageNumber());
  }

  @Test
  void normalPagesPassThrough() {
    assertEquals(10, Paging.capped(PageRequest.of(0, 10)).getPageSize());
  }

  @Test
  void defaultSortAppliesWhenUnsorted() {
    Pageable capped = Paging.capped(PageRequest.of(0, 10), Sort.by("createdAt").descending());
    assertEquals(Sort.by("createdAt").descending(), capped.getSort());
  }
}
