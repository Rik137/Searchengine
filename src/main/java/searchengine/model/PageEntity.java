package searchengine.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
@Entity
@Table(name = "page", indexes = {@Index(name = "index_path",
        columnList = "path")}, uniqueConstraints = @UniqueConstraint(columnNames = {"site_id","path"}))
@NoArgsConstructor
@Setter
@Getter
public class PageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private int id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;
    @Column(name="path", nullable = false, length = 255)
    private String path;
    @Column(name = "code", nullable = false)
    private int code;
    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    public PageEntity(SiteEntity site, String path) {
        this.site = site;
        this.path = path;
    }
}
