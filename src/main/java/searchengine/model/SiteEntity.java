package searchengine.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@NoArgsConstructor
@Getter
@Setter
@ToString(exclude = "pages")
@Entity
@Table(name="site", uniqueConstraints = @UniqueConstraint(columnNames = {"url"}))
public class SiteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private int id;
    @Enumerated(EnumType.STRING)  // чтобы enum сохранялся как строка ('INDEXING', 'INDEXED', 'FAILED')
    @Column(name = "status", nullable = false)
    private StatusEntity status;
    @Column(name="status_time", nullable = false)
    private LocalDateTime statusTime;
    @Column(name="last_error", columnDefinition = "TEXT")
    private String lastError;
    @Column(name="url", nullable = false, length = 255)
    private String url;
    @Column(name="name", nullable = false, length = 255)
    private String name;

    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PageEntity> pages = new ArrayList<>();

    public SiteEntity(StatusEntity status, LocalDateTime statusTime,
                      String lastError, String url, String name) {
        this.status = status;
        this.statusTime = statusTime;
        this.lastError = lastError;
        this.url = url;
        this.name = name;
    }
}