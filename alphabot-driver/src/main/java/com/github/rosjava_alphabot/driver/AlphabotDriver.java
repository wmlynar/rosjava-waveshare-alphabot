package com.github.rosjava_alphabot.driver;

import com.github.rosjava_alphabot.driver.dto.DistancesDto;
import com.github.rosjava_alphabot.driver.dto.VelocitiesDto;
import com.github.rosjava_alphabot.driver.hardware.AlphaBotConfig;
import com.github.rosjava_alphabot.driver.hardware.EncoderCounter;
import com.github.rosjava_alphabot.driver.hardware.Motor;
import com.github.rosjava_alphabot.driver.utils.Differentiator;
import com.github.rosjava_alphabot.driver.utils.MiniPID;

public class AlphabotDriver {
	
	public static double BASE_WIDTH = AlphaBotConfig.baseWidthInMeters;
	public static double TICKS_PER_METER = AlphaBotConfig.ticksPerMeter;
	
	private EncoderCounter counterLeft = new EncoderCounter(AlphaBotConfig.Side.LEFT);
	private EncoderCounter counterRight = new EncoderCounter(AlphaBotConfig.Side.RIGHT);
	private Motor motorLeft = new Motor(AlphaBotConfig.Side.LEFT);
	private Motor motorRight = new Motor(AlphaBotConfig.Side.RIGHT);
	private Differentiator distDifferentiatorLeft = new Differentiator(1e-6);
	private Differentiator distDifferentiatorRight = new Differentiator(1e-6);
	private MiniPID leftPid = new MiniPID(1, 0, 0, 50);
	private MiniPID rightPid = new MiniPID(1, 0, 0, 50);
	private volatile double setpointVelocityLeft;
	private volatile double setpointVelocityRight;
	
	private Object monitor = new Object();
	
	public AlphabotDriver() {
		// PWM output needed to move the robot because of static friction
		leftPid.setMaxIOutput(30);
		rightPid.setMaxIOutput(30);
		
		// output cannot be higher than 100, or the software PWM will go crazy
		leftPid.setOutputLimits(100);
		rightPid.setOutputLimits(100);
	}
	
	public void setPidParameters(double p, double i, double d, double f) {
		leftPid.setPID(p, i, d, f);
		rightPid.setPID(p, i, d, f);
	}
	
	public void startThreads() {
		counterLeft.startThread();
		counterRight.startThread();
		
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				while(true) {
					controlLoop();
				}
			}
		});
		t.start();
	}
	
	public DistancesDto getDistances() {
		
		DistancesDto dist = new DistancesDto();
		
		dist.left = counterLeft.getTicks() / TICKS_PER_METER;;
		dist.right = counterRight.getTicks() / TICKS_PER_METER;
		
		return dist;
	}

	public void processTwistMessage(VelocitiesDto twist) {
		synchronized (monitor) {
			setpointVelocityLeft = twist.velocityLeft;
			setpointVelocityRight = twist.velocityRight;
		}
	}


	private void controlLoop() {

		// get setpoints
		double setpointVelocityLeft;
		double setpointVelocityRight;
		
		synchronized (monitor) {
			setpointVelocityLeft = this.setpointVelocityLeft;
			setpointVelocityRight = this.setpointVelocityRight;
		}

		// get time
		double time = System.currentTimeMillis() / 1000.;
		
		// compute velocities
		double currentDistanceLeft = counterLeft.getTicks() / AlphaBotConfig.ticksPerMeter;
		double currentDistanceRight = counterRight.getTicks() / AlphaBotConfig.ticksPerMeter;
		
		double currentVelocityLeft = distDifferentiatorLeft.differentiate(time, currentDistanceLeft);
		double currentVelocityRight = distDifferentiatorRight.differentiate(time, currentDistanceRight);
		
		// get PID output
		int pwmLeft = (int) leftPid.getOutput(currentVelocityLeft, setpointVelocityLeft);
		int pwmRight = (int) rightPid.getOutput(currentVelocityRight, setpointVelocityRight);

		// set output
		motorLeft.setPWM(pwmLeft);
		motorRight.setPWM(pwmRight);

		// set direction of rotation for tick counting
		counterLeft.setForward(pwmLeft>=0);
		counterRight.setForward(pwmRight>=0);
		
		try {
			Thread.sleep(200);
		} catch (InterruptedException e) {
		}
	}
}
