package com.smartline.smartos.modules.user;

import java.util.List;

public final class OnboardingDtos {

  private OnboardingDtos() {
  }

  public static class ChecklistItem {
    private String key;
    private String label;
    private String description;
    private boolean completed;
    private boolean optional;
    private String href;

    public ChecklistItem() {
    }

    public ChecklistItem(String key, String label, String description, boolean completed,
        boolean optional, String href) {
      this.key = key;
      this.label = label;
      this.description = description;
      this.completed = completed;
      this.optional = optional;
      this.href = href;
    }

    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }

    public String getLabel() {
      return label;
    }

    public void setLabel(String label) {
      this.label = label;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public boolean isCompleted() {
      return completed;
    }

    public void setCompleted(boolean completed) {
      this.completed = completed;
    }

    public boolean isOptional() {
      return optional;
    }

    public void setOptional(boolean optional) {
      this.optional = optional;
    }

    public String getHref() {
      return href;
    }

    public void setHref(String href) {
      this.href = href;
    }
  }

  public static class ChecklistResponse {
    private List<ChecklistItem> items;
    private boolean complete;
    private boolean dismissed;

    public ChecklistResponse() {
    }

    public ChecklistResponse(List<ChecklistItem> items, boolean complete, boolean dismissed) {
      this.items = items;
      this.complete = complete;
      this.dismissed = dismissed;
    }

    public List<ChecklistItem> getItems() {
      return items;
    }

    public void setItems(List<ChecklistItem> items) {
      this.items = items;
    }

    public boolean isComplete() {
      return complete;
    }

    public void setComplete(boolean complete) {
      this.complete = complete;
    }

    public boolean isDismissed() {
      return dismissed;
    }

    public void setDismissed(boolean dismissed) {
      this.dismissed = dismissed;
    }
  }

  public static class UpdateOnboardingRequest {
    private Boolean themeSkipped;
    private Boolean dismissed;

    public UpdateOnboardingRequest() {
    }

    public Boolean getThemeSkipped() {
      return themeSkipped;
    }

    public void setThemeSkipped(Boolean themeSkipped) {
      this.themeSkipped = themeSkipped;
    }

    public Boolean getDismissed() {
      return dismissed;
    }

    public void setDismissed(Boolean dismissed) {
      this.dismissed = dismissed;
    }
  }
}
