package ch.supsi.imageprocessing.entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;

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

		@Column(nullable = false)
		private String storagePath;

		@ManyToOne
		@JoinColumn(nullable=false, name = "user_id")
		private User user;	

		public Image(){}

		public Image(String name, String storagePath, String format, User user){
				this.name = name;
				this.storagePath = storagePath;
				this.format = format;
				this.user= user;
		}

		public Long getId(){ return this.id; }

		public String getName(){ return this.name; }
		public void setName(String name){ this.name = name; }

		public String getFormat(){ return this.format; }
		public void setFormat(String format){ this.format = format; }

		public String getStoragePath(){ return this.storagePath; }
		public void setStoragePath(String storagePath){ this.storagePath = storagePath; }

		public User getUser() { return this.user; }
		public void setUser(User user) { this.user = user; }
}
