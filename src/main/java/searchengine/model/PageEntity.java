package searchengine.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
@NoArgsConstructor
@EqualsAndHashCode
@Setter
@Getter
@Entity
@Table(
        name = "page",
        uniqueConstraints = @UniqueConstraint(columnNames = {"site_id", "path"})
)

public class PageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private int id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;
    @Column(name="path", nullable = false, columnDefinition = "TEXT")
    //length = 255
    private String path;
    @Column(name = "code", nullable = false)
    private int code;
    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @OneToMany(mappedBy = "page", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SearchIndexEntity> indexes = new ArrayList<>();


    public PageEntity(SiteEntity site, String path, int code, String content, List<SearchIndexEntity> indexes) {
        this.site = site;
        this.path = path;
        this.code = code;
        this.content = content;
        this.indexes = indexes;
    }
}
