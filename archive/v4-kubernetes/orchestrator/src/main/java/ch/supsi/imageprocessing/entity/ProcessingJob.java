package ch.supsi.imageprocessing.entity;

import ch.supsi.imageprocessing.common.enums.JobStatus;
import ch.supsi.imageprocessing.common.enums.JobType;

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
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;


@Entity 
@Table(
		name = "processingJob",
		uniqueConstraints = {
				@UniqueConstraint(columnNames = {"image_id", "outputName"})
		}
)
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
		@OnDelete(action = OnDeleteAction.CASCADE)
		private Image image; 

		@Column(nullable = false)
		private String outputName; 

		@Column(nullable = true)
		private String targetStorageKey;

		@Column(nullable = false)
		private String targetFormat; 

		public ProcessingJob(){}

		public ProcessingJob(Image image, JobType type, String outputName, String targetFormat) {
		    this.image = image;
		    this.type = type;
		    this.outputName = outputName;
		    this.targetFormat = targetFormat.toLowerCase();
		    this.status = JobStatus.PENDING;
		}

		public Long getId(){ return this.id; }
		
		public Image getImage(){ return this.image; }
		public void setImage(Image image){ this.image= image; }

		public String getOutputName(){ return this.outputName; }
		public void setOutputName(String outputName){ this.outputName = outputName; }

		public JobType getType(){ return this.type; }
		public void setType(JobType type){ this.type = type; }

		public JobStatus getStatus(){ return this.status; }
		public void setStatus(JobStatus status){ this.status = status; }

		public String getTargetStorageKey(){ return this.targetStorageKey; }
		public void setTargetStorageKey(String targetStorageKey){ this.targetStorageKey= targetStorageKey; }

		public String getTargetFormat(){ return this.targetFormat; }
		public void setTargetFormat(String targetFormat){ this.targetFormat = targetFormat.toLowerCase(); }
}
