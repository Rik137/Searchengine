package searchengine.model;

import lombok.*;

import javax.persistence.*;

@Setter
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Entity
@Table(name = "search_index",
        uniqueConstraints = @UniqueConstraint(columnNames = {"page_id", "lemma_id"}))
public class SearchIndexEntity {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false)
    private PageEntity page;
    @Column(name = "rank_value", nullable = false)
    private float rank;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lemma_id", nullable = false)
    private LemmaEntity lemma;

    public SearchIndexEntity(PageEntity page, float rank, LemmaEntity lemma) {
        this.page = page;
        this.rank = rank;
        this.lemma = lemma;
    }
}
