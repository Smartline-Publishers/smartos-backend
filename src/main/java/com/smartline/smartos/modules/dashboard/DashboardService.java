package com.smartline.smartos.modules.dashboard;

import com.smartline.smartos.modules.audit.AuditLog;
import com.smartline.smartos.modules.audit.AuditLogRepository;
import com.smartline.smartos.modules.book.Book;
import com.smartline.smartos.modules.book.Book.BookStatus;
import com.smartline.smartos.modules.book.BookRepository;
import com.smartline.smartos.modules.book.ContributorRepository;
import com.smartline.smartos.modules.dashboard.DashboardDtos.ActivityItem;
import com.smartline.smartos.modules.dashboard.DashboardDtos.ActivityScope;
import com.smartline.smartos.modules.dashboard.DashboardDtos.AttentionItem;
import com.smartline.smartos.modules.dashboard.DashboardDtos.DashboardSummary;
import com.smartline.smartos.modules.dashboard.DashboardDtos.PipelineSlice;
import com.smartline.smartos.modules.dashboard.DashboardDtos.Trends;
import com.smartline.smartos.modules.finance.RoyaltyStatement;
import com.smartline.smartos.modules.finance.RoyaltyStatementRepository;
import com.smartline.smartos.modules.finance.Budget;
import com.smartline.smartos.modules.finance.BudgetRepository;
import com.smartline.smartos.modules.finance.Transaction;
import com.smartline.smartos.modules.finance.Transaction.TransactionType;
import com.smartline.smartos.modules.finance.TransactionRepository;
import com.smartline.smartos.modules.inventory.InventoryService;
import com.smartline.smartos.modules.inventory.InventoryDtos.StockLevelResponse;
import com.smartline.smartos.modules.inventory.WarehouseRepository;
import com.smartline.smartos.modules.manuscript.Manuscript;
import com.smartline.smartos.modules.manuscript.Manuscript.ManuscriptStage;
import com.smartline.smartos.modules.manuscript.ManuscriptRepository;
import com.smartline.smartos.modules.production.ProductionJob.JobStatus;
import com.smartline.smartos.modules.production.ProductionJobRepository;
import com.smartline.smartos.modules.sales.CustomerRepository;
import com.smartline.smartos.modules.sales.Order.OrderStatus;
import com.smartline.smartos.modules.sales.OrderRepository;
import com.smartline.smartos.modules.task.Task;
import com.smartline.smartos.modules.task.Task.TaskStatus;
import com.smartline.smartos.modules.task.TaskRepository;
import com.smartline.smartos.modules.user.AppUserRepository;
import com.smartline.smartos.modules.user.InviteRepository;
import com.smartline.smartos.security.Permission;
import com.smartline.smartos.security.RolePermissions;
import com.smartline.smartos.security.UserPrincipal;
import com.smartline.smartos.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

  private static final int LOW_STOCK_THRESHOLD = 10;
  private static final int MAX_ATTENTION = 8;

  private final BookRepository books;
  private final ContributorRepository contributors;
  private final ManuscriptRepository manuscripts;
  private final TaskRepository tasks;
  private final RoyaltyStatementRepository statements;
  private final AuditLogRepository auditLogs;
  private final AppUserRepository users;
  private final InviteRepository invites;
  private final InventoryService inventory;
  private final OrderRepository orders;
  private final CustomerRepository customers;
  private final TransactionRepository transactions;
  private final BudgetRepository budgets;
  private final ProductionJobRepository productionJobs;
  private final WarehouseRepository warehouses;

  public DashboardService(BookRepository books, ContributorRepository contributors,
      ManuscriptRepository manuscripts, TaskRepository tasks,
      RoyaltyStatementRepository statements, AuditLogRepository auditLogs,
      AppUserRepository users, InviteRepository invites, InventoryService inventory,
      OrderRepository orders, CustomerRepository customers, TransactionRepository transactions,
      BudgetRepository budgets, ProductionJobRepository productionJobs,
      WarehouseRepository warehouses) {
    this.books = books;
    this.contributors = contributors;
    this.manuscripts = manuscripts;
    this.tasks = tasks;
    this.statements = statements;
    this.auditLogs = auditLogs;
    this.users = users;
    this.invites = invites;
    this.inventory = inventory;
    this.orders = orders;
    this.customers = customers;
    this.transactions = transactions;
    this.budgets = budgets;
    this.productionJobs = productionJobs;
    this.warehouses = warehouses;
  }

  /**
   * Builds the dashboard for the caller's role.
   *
   * <p>Each section is included only when the caller's role actually holds the
   * permission that governs it, so an editor never sees money and a finance
   * user never sees the editorial pipeline. The API is the single source of
   * truth: the web app renders exactly what this returns.
   *
   * <p>Activity is always scoped: without {@code audit.read} the feed contains
   * only the caller's own entries. Organisation-wide activity requires that
   * administrative permission and is still limited to the caller's own
   * organisation, resolved from the authenticated account rather than from any
   * request parameter.
   */
  public DashboardSummary summary(UserPrincipal principal) {
    UUID orgId = TenantContext.require();
    Set<Permission> perms = RolePermissions.forRoleNames(principal.getRoles());
    boolean canViewBooks = perms.contains(Permission.BOOKS_READ);
    boolean canViewEditorial = perms.contains(Permission.MANUSCRIPTS_READ);
    boolean canViewContributors = perms.contains(Permission.CONTRIBUTORS_READ);
    boolean canViewFinance = perms.contains(Permission.FINANCE_READ)
        || perms.contains(Permission.ROYALTIES_READ);
    boolean canViewLedger = perms.contains(Permission.FINANCE_READ);
    boolean canViewMembers = perms.contains(Permission.USERS_READ);
    boolean canViewOrders = perms.contains(Permission.ORDERS_READ);
    boolean canViewCustomers = perms.contains(Permission.CUSTOMERS_READ);
    boolean canViewProduction = perms.contains(Permission.PRODUCTION_READ);
    boolean canViewInventory = perms.contains(Permission.INVENTORY_READ);
    boolean canViewWarehouses = perms.contains(Permission.WAREHOUSES_READ);

    List<Book> allBooks = canViewBooks
        ? books.findByOrganization_Id(orgId, Pageable.unpaged()).getContent()
        : List.of();
    long published = allBooks.stream().filter(b -> b.getStatus() == BookStatus.PUBLISHED).count();
    int totalContributors = canViewContributors
        ? contributors.findByOrganization_Id(orgId).size()
        : 0;
    List<RoyaltyStatement> allStatements = canViewFinance
        ? statements.findByOrganization_Id(orgId, Pageable.unpaged()).getContent()
        : List.of();
    BigDecimal pending = BigDecimal.ZERO;
    for (RoyaltyStatement s : allStatements) {
      if (s.getStatus() == RoyaltyStatement.StatementStatus.DRAFT
          || s.getStatus() == RoyaltyStatement.StatementStatus.APPROVED) {
        pending = pending.add(s.getPayable());
      }
    }
    List<Manuscript> allManuscripts = canViewEditorial
        ? manuscripts.findByOrganization_Id(orgId, Pageable.unpaged()).getContent()
        : List.of();

    // Stock is read once: the same snapshot feeds both the attention list and
    // the inventory metrics, so the two can never disagree within a response.
    List<StockLevelResponse> stockLevels =
        canViewInventory ? inventory.levels(null, null, null) : List.of();
    int stockOnHand = stockLevels.stream().mapToInt(StockLevelResponse::getQuantity).sum();
    int inventoryTitles = (int) stockLevels.stream()
        .filter(l -> l.getQuantity() > 0)
        .map(StockLevelResponse::getBookId)
        .distinct()
        .count();
    int lowStockItems = (int) stockLevels.stream()
        .filter(l -> l.getQuantity() < LOW_STOCK_THRESHOLD)
        .count();

    // Money and budget figures are limited to the current calendar quarter and
    // only for callers who may read the ledger. Royalty-only readers still see
    // the pending royalty liability but never ledger revenue or budgets.
    BigDecimal revenueThisQuarter = BigDecimal.ZERO;
    BigDecimal budgetThisQuarter = BigDecimal.ZERO;
    if (canViewLedger) {
      LocalDate today = LocalDate.now(ZoneOffset.UTC);
      Month firstMonth = today.getMonth().firstMonthOfQuarter();
      LocalDate quarterStart = LocalDate.of(today.getYear(), firstMonth, 1);
      LocalDate quarterEnd = quarterStart.plusMonths(3).minusDays(1);
      Instant start = quarterStart.atStartOfDay(ZoneOffset.UTC).toInstant();
      Instant end = quarterEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
      for (Transaction t : transactions.findByOrganization_IdAndCreatedAtBetween(orgId, start,
          end)) {
        if (t.getAmount() == null || t.getType() == null) {
          continue;
        }
        if (t.getType() == TransactionType.SALE) {
          revenueThisQuarter = revenueThisQuarter.add(t.getAmount());
        } else if (t.getType() == TransactionType.REFUND) {
          revenueThisQuarter = revenueThisQuarter.subtract(t.getAmount());
        }
      }
      for (Budget b : budgets.findByOrganization_Id(orgId)) {
        if (b.getPlannedAmount() != null && b.getPeriodStart() != null
            && b.getPeriodEnd() != null
            && !b.getPeriodEnd().isBefore(quarterStart)
            && !b.getPeriodStart().isAfter(quarterEnd)) {
          budgetThisQuarter = budgetThisQuarter.add(b.getPlannedAmount());
        }
      }
    }

    // Activity is personal by default. Only a caller who holds the
    // administrative audit permission may see the organisation-wide feed, and
    // even then the query stays inside the organisation from the token.
    boolean canViewOrgActivity = perms.contains(Permission.AUDIT_READ);

    DashboardSummary summary = new DashboardSummary();
    summary.setTotalBooks(allBooks.size());
    summary.setPublishedBooks((int) published);
    summary.setTotalContributors(totalContributors);
    summary.setPendingRoyalties(canViewFinance ? pending : BigDecimal.ZERO);
    summary.setTeamMembers(canViewMembers ? (int) users.countByOrganization_IdAndActiveTrue(orgId) : 0);
    summary.setPendingInvites(canViewMembers
        ? (int) invites.countByOrganizationIdAndAcceptedAtIsNullAndExpiresAtAfter(orgId,
            Instant.now())
        : 0);
    summary.setTotalCustomers(canViewCustomers ? (int) customers.countByOrganization_Id(orgId) : 0);
    summary.setActiveOrders(canViewOrders
        ? (int) orders.countByOrganization_IdAndStatusIn(orgId,
            List.of(OrderStatus.DRAFT, OrderStatus.CONFIRMED))
        : 0);
    summary.setRevenueThisQuarter(revenueThisQuarter);
    summary.setBudgetThisQuarter(budgetThisQuarter);
    summary.setActiveProductionJobs(canViewProduction
        ? (int) productionJobs.countByOrganization_IdAndStatusIn(orgId,
            List.of(JobStatus.PLANNED, JobStatus.IN_PROGRESS))
        : 0);
    summary.setInventoryTitles(inventoryTitles);
    summary.setStockOnHand(stockOnHand);
    summary.setLowStockItems(lowStockItems);
    summary.setWarehouses(canViewWarehouses ? (int) warehouses.countByOrganization_Id(orgId) : 0);
    summary.setManuscriptPipeline(canViewEditorial
        ? manuscriptPipeline(allManuscripts)
        : List.of());
    summary.setTitlePipeline(canViewBooks ? titlePipeline(allBooks) : List.of());
    summary.setAttention(attention(orgId, allManuscripts, stockLevels, perms));
    summary.setActivityScope(canViewOrgActivity ? ActivityScope.ORGANIZATION : ActivityScope.SELF);
    summary.setActivity(activity(orgId, canViewOrgActivity ? null : principal.getUserId()));
    summary.setTrends(trends(allBooks, allStatements, canViewFinance));
    return summary;
  }

  private List<PipelineSlice> manuscriptPipeline(List<Manuscript> all) {
    Map<ManuscriptStage, Integer> counts = new HashMap<>();
    for (Manuscript m : all) {
      counts.merge(m.getStage(), 1, Integer::sum);
    }
    List<PipelineSlice> slices = new ArrayList<>();
    slices.add(new PipelineSlice("Submitted", counts.getOrDefault(ManuscriptStage.SUBMITTED, 0)));
    slices.add(new PipelineSlice("In review", counts.getOrDefault(ManuscriptStage.REVIEW, 0)));
    slices.add(new PipelineSlice("Editing", counts.getOrDefault(ManuscriptStage.EDITING, 0)));
    slices.add(new PipelineSlice("Proof", counts.getOrDefault(ManuscriptStage.PROOF, 0)));
    slices.add(new PipelineSlice("Approved", counts.getOrDefault(ManuscriptStage.APPROVED, 0)));
    return slices;
  }

  private List<PipelineSlice> titlePipeline(List<Book> all) {
    Map<BookStatus, Integer> counts = new HashMap<>();
    for (Book b : all) {
      counts.merge(b.getStatus(), 1, Integer::sum);
    }
    List<PipelineSlice> slices = new ArrayList<>();
    slices.add(new PipelineSlice("Draft", counts.getOrDefault(BookStatus.DRAFT, 0)));
    slices.add(new PipelineSlice("In edit", counts.getOrDefault(BookStatus.IN_EDIT, 0)));
    slices.add(new PipelineSlice("Designed", counts.getOrDefault(BookStatus.DESIGNED, 0)));
    slices.add(new PipelineSlice("Printing", counts.getOrDefault(BookStatus.PRINTING, 0)));
    slices.add(new PipelineSlice("Published", counts.getOrDefault(BookStatus.PUBLISHED, 0)));
    slices.add(new PipelineSlice("Archived", counts.getOrDefault(BookStatus.ARCHIVED, 0)));
    return slices;
  }

  /**
   * Items the caller can actually act on. Each source is gated by the
   * permission that governs it, so a role never sees a nudge toward a screen
   * it cannot open.
   */
  private List<AttentionItem> attention(UUID orgId, List<Manuscript> allManuscripts,
      List<StockLevelResponse> stockLevels, Set<Permission> perms) {
    List<AttentionItem> items = new ArrayList<>();
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    if (perms.contains(Permission.TASKS_READ)) {
      tasks.findByOrganization_Id(orgId, Pageable.unpaged()).getContent().stream()
          .filter(t -> t.getStatus() != TaskStatus.DONE
              && t.getDueDate() != null && t.getDueDate().isBefore(today))
          .sorted(Comparator.comparing(Task::getDueDate))
          .limit(3)
          .forEach(t -> items.add(new AttentionItem("task-" + t.getId(), "task",
              "Overdue: " + t.getTitle(), "Was due " + t.getDueDate(), "high",
              "Overdue " + t.getDueDate(), "/tasks")));
    }
    if (perms.contains(Permission.INVENTORY_READ)) {
      stockLevels.stream()
          .filter(l -> l.getQuantity() < LOW_STOCK_THRESHOLD)
          .sorted(Comparator.comparingInt(StockLevelResponse::getQuantity))
          .limit(3)
          .forEach(l -> items.add(new AttentionItem("stock-" + l.getBookId() + "-" + l.getWarehouseId(),
              "stock", "Low stock — " + l.getBookTitle(),
              l.getQuantity() + " copies left at " + l.getWarehouseName(), "medium", null,
              "/inventory")));
    }
    if (perms.contains(Permission.MANUSCRIPTS_WRITE)) {
      allManuscripts.stream()
          .filter(m -> m.getStage() == ManuscriptStage.SUBMITTED
              || m.getStage() == ManuscriptStage.REVIEW)
          .sorted(Comparator.comparing(Manuscript::getCreatedAt,
              Comparator.nullsLast(Comparator.naturalOrder())))
          .limit(2)
          .forEach(m -> items.add(new AttentionItem("manuscript-" + m.getId(), "approval",
              "Review needed — " + m.getTitle(), "Awaiting editorial decision", "medium", null,
              "/manuscripts")));
    }
    if (perms.contains(Permission.ROYALTIES_WRITE)) {
      long drafts = statements.findByOrganization_IdAndStatus(orgId,
              RoyaltyStatement.StatementStatus.DRAFT, Pageable.unpaged()).getTotalElements();
      if (drafts > 0) {
        items.add(new AttentionItem("royalties-draft", "royalty",
            drafts + (drafts == 1 ? " royalty statement" : " royalty statements")
                + " awaiting approval",
            "Review and approve pending payouts", "medium", null, "/royalties"));
      }
    }
    return items.stream().limit(MAX_ATTENTION).toList();
  }

  /**
   * Recent audit entries for the dashboard feed.
   *
   * @param actorId when non-null, restricts the feed to this actor only. The
   *                organisation id is always applied as well, so a call can
   *                never return another organisation's activity.
   */
  private List<ActivityItem> activity(UUID orgId, UUID actorId) {
    List<AuditLog> logs = actorId != null
        ? auditLogs.findTop8ByOrganizationIdAndActorIdOrderByCreatedAtDesc(orgId, actorId)
        : auditLogs.findTop8ByOrganizationIdOrderByCreatedAtDesc(orgId);
    if (logs.isEmpty()) {
      return List.of();
    }
    List<UUID> actorIds = logs.stream().map(AuditLog::getActorId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
    Map<UUID, String> names = new HashMap<>();
    if (!actorIds.isEmpty()) {
      // Resolve names within the same organisation as the feed, never globally.
      users.findByOrganization_IdAndIdIn(orgId, actorIds)
          .forEach(u -> names.put(u.getId(), u.getFullName()));
    }
    List<ActivityItem> feed = new ArrayList<>();
    for (AuditLog log : logs) {
      String actor = log.getActorId() != null
          ? names.getOrDefault(log.getActorId(), "Staff")
          : "System";
      feed.add(new ActivityItem(log.getId().toString(), actor, humanize(log.getAction()),
          log.getCreatedAt() != null ? log.getCreatedAt().toString() : null));
    }
    return feed;
  }

  private String humanize(String action) {
    if (action == null || action.isBlank()) {
      return "performed an action";
    }
    return switch (action) {
      case "book.create", "customer.create", "order.create", "task.create", "warehouse.create",
           "production.job.create", "production.run.create", "finance.budget.create",
           "royalty.contract.create", "user.create", "organization.create", "manuscript.create",
           "contributor.create" ->
        "created " + subject(action);
      case "book.update", "customer.update", "task.update", "manuscript.update",
           "finance.budget.update", "user.update" ->
        "updated " + subject(action);
      case "book.delete", "customer.delete", "task.delete", "warehouse.delete",
           "production.job.delete", "production.run.delete", "finance.budget.delete",
           "royalty.contract.delete", "user.delete", "manuscript.delete" ->
        "deleted " + subject(action);
      case "manuscript.transition" -> "moved a manuscript to a new stage";
      case "order.confirm" -> "confirmed an order";
      case "order.fulfil" -> "fulfilled an order";
      case "order.cancel" -> "cancelled an order";
      case "production.job.start" -> "started a print job";
      case "production.job.complete" -> "completed a print job";
      case "production.job.cancel" -> "cancelled a print job";
      case "production.run.post" -> "posted a print run into stock";
      case "stock.movement" -> "recorded a stock movement";
      case "stock.transfer" -> "transferred stock";
      case "finance.transaction.record" -> "recorded a transaction";
      case "royalty.contract.terminate" -> "terminated a contract";
      case "royalty.statement.generate" -> "generated a royalty statement";
      case "royalty.statement.approve" -> "approved a royalty statement";
      case "royalty.statement.pay" -> "paid a royalty statement";
      default -> action.replace('.', ' ').replace('_', ' ');
    };
  }

  private String subject(String action) {
    String[] parts = action.split("\\.", 2);
    if (parts.length < 2) {
      return "a record";
    }
    return switch (parts[0] + "." + parts[1]) {
      case "production.run" -> "a print run";
      case "production.job" -> "a print job";
      case "finance.budget" -> "a budget";
      case "finance.transaction" -> "a transaction";
      case "royalty.contract" -> "a contract";
      case "royalty.statement" -> "a statement";
      case "order.create", "order.update", "order.delete" -> "an order";
      case "organization.create" -> "an organization";
      default -> "a " + parts[0];
    };
  }

  private Trends trends(List<Book> allBooks, List<RoyaltyStatement> allStatements,
      boolean canViewFinance) {
    List<YearMonth> months = new ArrayList<>();
    YearMonth now = YearMonth.now(ZoneOffset.UTC);
    for (int i = 11; i >= 0; i--) {
      months.add(now.minusMonths(i));
    }
    List<Integer> bookCounts = new ArrayList<>();
    List<BigDecimal> royaltySums = new ArrayList<>();
    for (YearMonth month : months) {
      int titles = 0;
      for (Book b : allBooks) {
        if (b.getCreatedAt() != null
            && !YearMonth.from(b.getCreatedAt().atZone(ZoneOffset.UTC)).isAfter(month)) {
          titles++;
        }
      }
      bookCounts.add(titles);
      BigDecimal royalties = BigDecimal.ZERO;
      if (canViewFinance) {
        for (RoyaltyStatement s : allStatements) {
          if (s.getCreatedAt() != null
              && YearMonth.from(s.getCreatedAt().atZone(ZoneOffset.UTC)).equals(month)) {
            royalties = royalties.add(s.getPayable());
          }
        }
      }
      royaltySums.add(royalties);
    }
    return new Trends(bookCounts, royaltySums);
  }
}
