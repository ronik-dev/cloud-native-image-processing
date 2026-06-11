package ch.supsi.imageprocessing.entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity 
@Table(name = "users")
public class User{
		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		private Long id;

		@Column(nullable = false, unique = true)
		private String username; 

		@Column(nullable = false, unique = true)
		private String email;

		public User(){}

		public User(String username, String email){
				this.username = username;
				this.email = email;
		}

		public Long getId(){ return this.id; }
		
		public String getUsername(){ return this.username; }
		public void setUsername(String username){ this.username = username; }

		public String getEmail(){ return this.email; }
		public void setEmail(String email){ this.email = email; }
}
