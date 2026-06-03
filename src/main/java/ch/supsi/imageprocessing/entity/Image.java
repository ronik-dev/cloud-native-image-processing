package ch.supsi.imageprocessing.entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import java.time.LocalDateTime;   
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity 
@Table(name = "image")
public class Image{
		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		private Long id;

		@Column(nullable = false, unique = true)
		private String name; 

		@Column(nullable = false)
		private String format;

		@Column(unique=true, nullable = false)
		private String storageKey;

		@ManyToOne
		@JoinColumn(nullable=false, name = "user_id")
		@OnDelete(action = OnDeleteAction.CASCADE)
		private User user;	

		@Column(nullable = false, updatable = false)
		private LocalDateTime uploadedAt; 

		public Image(){}

		public Image(String name, String storageKey, String format, User user){
				this.name = name;
				this.storageKey = storageKey;
				this.format = format;
				this.user= user;
		}

		@PrePersist
		protected void onCreate() {
				this.uploadedAt = LocalDateTime.now();
		}

		public Long getId(){ return this.id; }

		public String getName(){ return this.name; }
		public void setName(String name){ this.name = name; }

		public String getFormat(){ return this.format; }
		public void setFormat(String format){ this.format = format; }

		public String getStorageKey(){ return this.storageKey; }
		public void setStorageKey(String storageKey){ this.storageKey= storageKey; }

		public User getUser() { return this.user; }
		public void setUser(User user) { this.user = user; }

		public LocalDateTime getUploadedAt() { return this.uploadedAt; }
}
