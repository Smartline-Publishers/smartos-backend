package com.smartline.smartos.modules.dashboard;

import java.math.BigDecimal;
import java.util.List;

/**
 * View-model for GET /api/v1/dashboard/summary. Field names and the friendly
 * stage labels intentionally match the frontend DashboardSummary contract.
 */
public final class DashboardDtos {

  private DashboardDtos() {
  }

  /**
   * Who the activity feed describes. {@code SELF} rows are strictly the
   * caller's own actions; {@code ORGANIZATION} rows cover the caller's whole
   * organisation and are only produced for holders of {@code audit.read}.
   */
  public enum ActivityScope {
    SELF,
    ORGANIZATION
  }

  public static class PipelineSlice {
    private String stage;
    private int count;

    public PipelineSlice() {
    }

    public PipelineSlice(String stage, int count) {
      this.stage = stage;
      this.count = count;
    }

    public String getStage() {
      return stage;
    }

    public void setStage(String stage) {
      this.stage = stage;
    }

    public int getCount() {
      return count;
    }

    public void setCount(int count) {
      this.count = count;
    }
  }

  public static class AttentionItem {
    private String id;
    private String kind;
    private String title;
    private String detail;
    private String severity;
    private String dueLabel;
    private String href;

    public AttentionItem() {
    }

    public AttentionItem(String id, String kind, String title, String detail, String severity,
        String dueLabel, String href) {
      this.id = id;
      this.kind = kind;
      this.title = title;
      this.detail = detail;
      this.severity = severity;
      this.dueLabel = dueLabel;
      this.href = href;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getKind() {
      return kind;
    }

    public void setKind(String kind) {
      this.kind = kind;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public String getDetail() {
      return detail;
    }

    public void setDetail(String detail) {
      this.detail = detail;
    }

    public String getSeverity() {
      return severity;
    }

    public void setSeverity(String severity) {
      this.severity = severity;
    }

    public String getDueLabel() {
      return dueLabel;
    }

    public void setDueLabel(String dueLabel) {
      this.dueLabel = dueLabel;
    }

    public String getHref() {
      return href;
    }

    public void setHref(String href) {
      this.href = href;
    }
  }

  public static class ActivityItem {
    private String id;
    private String actor;
    private String action;
    private String createdAt;

    public ActivityItem() {
    }

    public ActivityItem(String id, String actor, String action, String createdAt) {
      this.id = id;
      this.actor = actor;
      this.action = action;
      this.createdAt = createdAt;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getActor() {
      return actor;
    }

    public void setActor(String actor) {
      this.actor = actor;
    }

    public String getAction() {
      return action;
    }

    public void setAction(String action) {
      this.action = action;
    }

    public String getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(String createdAt) {
      this.createdAt = createdAt;
    }
  }

  public static class Trends {
    private List<Integer> books;
    private List<BigDecimal> royalties;

    public Trends() {
    }

    public Trends(List<Integer> books, List<BigDecimal> royalties) {
      this.books = books;
      this.royalties = royalties;
    }

    public List<Integer> getBooks() {
      return books;
    }

    public void setBooks(List<Integer> books) {
      this.books = books;
    }

    public List<BigDecimal> getRoyalties() {
      return royalties;
    }

    public void setRoyalties(List<BigDecimal> royalties) {
      this.royalties = royalties;
    }
  }

  public static class DashboardSummary {
    private int totalBooks;
    private int publishedBooks;
    private int totalContributors;
    private BigDecimal pendingRoyalties;
    private List<PipelineSlice> manuscriptPipeline;
    private List<PipelineSlice> titlePipeline;
    private List<AttentionItem> attention;
    private List<ActivityItem> activity;
    private ActivityScope activityScope;
    private Trends trends;

    public DashboardSummary() {
    }

    public int getTotalBooks() {
      return totalBooks;
    }

    public void setTotalBooks(int totalBooks) {
      this.totalBooks = totalBooks;
    }

    public int getPublishedBooks() {
      return publishedBooks;
    }

    public void setPublishedBooks(int publishedBooks) {
      this.publishedBooks = publishedBooks;
    }

    public int getTotalContributors() {
      return totalContributors;
    }

    public void setTotalContributors(int totalContributors) {
      this.totalContributors = totalContributors;
    }

    public BigDecimal getPendingRoyalties() {
      return pendingRoyalties;
    }

    public void setPendingRoyalties(BigDecimal pendingRoyalties) {
      this.pendingRoyalties = pendingRoyalties;
    }

    public List<PipelineSlice> getManuscriptPipeline() {
      return manuscriptPipeline;
    }

    public void setManuscriptPipeline(List<PipelineSlice> manuscriptPipeline) {
      this.manuscriptPipeline = manuscriptPipeline;
    }

    public List<PipelineSlice> getTitlePipeline() {
      return titlePipeline;
    }

    public void setTitlePipeline(List<PipelineSlice> titlePipeline) {
      this.titlePipeline = titlePipeline;
    }

    public List<AttentionItem> getAttention() {
      return attention;
    }

    public void setAttention(List<AttentionItem> attention) {
      this.attention = attention;
    }

    public List<ActivityItem> getActivity() {
      return activity;
    }

    public void setActivity(List<ActivityItem> activity) {
      this.activity = activity;
    }

    public ActivityScope getActivityScope() {
      return activityScope;
    }

    public void setActivityScope(ActivityScope activityScope) {
      this.activityScope = activityScope;
    }

    public Trends getTrends() {
      return trends;
    }

    public void setTrends(Trends trends) {
      this.trends = trends;
    }
  }
}
