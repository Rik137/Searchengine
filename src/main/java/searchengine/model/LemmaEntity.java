package searchengine.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
@Setter
@Getter
@NoArgsConstructor
@Entity
@Table(name = "lemmas", indexes = {
        @Index(name = "idx_lemma_site", columnList = "lemma, site_id")
},uniqueConstraints = @UniqueConstraint(columnNames = {"lemma", "site_id"}))
public final class LemmaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;
    @Column(nullable = false, length=255)
    private String lemma;
    @Column(nullable = false)
    private int frequency;

    @OneToMany(mappedBy = "lemma", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SearchIndexEntity> indexes = new ArrayList<>();
}
