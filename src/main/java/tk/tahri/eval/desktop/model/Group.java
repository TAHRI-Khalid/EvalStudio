package tk.tahri.eval.desktop.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "\"groups\"")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name;
    private String etablissement;
    private String section;
    @Column(name = "academic_year")
    private String year;
    private String logoUrl;
    private String theme;
    private Instant createdAt = Instant.now();

    public Group() {
    }

    public Group(String id, String name, String etablissement, String section, String year, String logoUrl, String theme, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.etablissement = etablissement;
        this.section = section;
        this.year = year;
        this.logoUrl = logoUrl;
        this.theme = theme;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEtablissement() {
        return etablissement;
    }

    public void setEtablissement(String etablissement) {
        this.etablissement = etablissement;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    // Record-style accessors
    public String id() { return id; }
    public String name() { return name; }
    public String etablissement() { return etablissement; }
    public String section() { return section; }
    public String year() { return year; }
    public String logoUrl() { return logoUrl; }
    public String theme() { return theme; }

    // Compatibility aliases with old Course model
    public String getTitle() { return name; }
    public void setTitle(String title) { this.name = title; }
    public String title() { return name; }

    public String getInstitute() { return etablissement; }
    public void setInstitute(String institute) { this.etablissement = institute; }
    public String institute() { return etablissement; }

    @Override
    public String toString() {
        return name != null ? name : "Sans nom";
    }
}
