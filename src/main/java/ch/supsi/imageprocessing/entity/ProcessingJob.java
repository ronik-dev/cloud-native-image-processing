package ch.supsi.imageprocessing.entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

@Entity 
@Table(name = "processingJob")
public class ProcessingJob{
		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		private Long id;

		@Enumerated(EnumType.STRING)
		@Column(nullable = false)
		private JobType type; 


		@Enumerated(EnumType.STRING)
		@Column(nullable = false)
		private JobStatus status = JobStatus.PENDING; 

		@ManyToOne
		@JoinColumn(nullable=false, name = "image_id")
		private Image image; 

		@Column(nullable = false, unique = true)
		private String outputName; 

		public ProcessingJob(){}

		public ProcessingJob(Image image, JobType type ,String outputName){
				this.type= type;
				this.outputName= outputName;
				this.image= image;
		}

		public Long getId(){ return this.id; }
		
		public Image getImage(){ return this.image; }
		public void setImage(Image image){ this.image= image; }

		public String getOutputName(){ return this.outputName; }
		public void setOutputName(String outputName){ this.outputName = outputName; }

		public JobType getType(){ return this.type; }
		public void setType(JobType type){ this.type = type; }

		public JobStatus getStatus(){ return this.status; }
		public void setStatus(JobStatus status){ this.status= status; }
}
