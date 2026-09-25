package model;

public class AppInfo {
    private String name;
    private String icon;
    private boolean productive;
    private String installedDate;
    private String lastUsedDate;

    public AppInfo(String name, String icon, boolean productive,
                   String installedDate, String lastUsedDate) {
        this.name = name;
        this.icon = icon;
        this.productive = productive;
        this.installedDate = installedDate;
        this.lastUsedDate = lastUsedDate;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public boolean isProductive() { return productive; }
    public void setProductive(boolean productive) { this.productive = productive; }

    public String getInstalledDate() { return installedDate; }
    public void setInstalledDate(String date) { this.installedDate = date; }

    public String getLastUsedDate() { return lastUsedDate; }
    public void setLastUsedDate(String date) { this.lastUsedDate = date; }

    @Override
    public String toString() { return name; }
}