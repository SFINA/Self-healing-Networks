package ModifiedJSwarmMain;
import network.FlowNetwork;

//import input.Backend;
import backend.FlowBackendInterface;
import input.Domain;
import input.SfinaParameter;
import input.TopologyLoader;
import java.util.HashMap;
import network.FlowNetwork;
import network.Node;
import power.backend.PowerFlowType;
import power.backend.InterpssFlowBackend;
import power.input.PowerFlowLoader;
import power.input.PowerLinkState;
import power.input.PowerNodeState;
import power.backend.PowerBackendParameter;


/**
 * Base Fitness Function
 * @author Pablo Cingolani <pcingola@users.sourceforge.net>
 */
public abstract class FitnessFunction {
    
        private FlowNetwork flowNetwork;
        private int flowgate_position;

	/** Should this function be maximized or minimized */
	boolean maximize;

	//-------------------------------------------------------------------------
	// Constructors
	//-------------------------------------------------------------------------

	/** Default constructor */
        
	public FitnessFunction(FlowNetwork net, int flowgate_pos) {
		maximize = true; // Default: Maximize //this was just maximize = true 
                this.flowNetwork = net;
                this.flowgate_position=flowgate_pos;
	}

	/**
	 * Constructor 
	 * @param maximize : Should we try to maximize or minimize this function?
	 */
	public FitnessFunction(boolean maximize) {
		this.maximize = maximize;
	}

	//-------------------------------------------------------------------------
	// Methods
	//-------------------------------------------------------------------------

	/**
	 * Evaluates a particles at a given position
	 * NOTE: You should write your own method!
	 * 
         * @param flowNetwork : flowNetwork
	 * @param position : Particle's position
	 * @return Fitness function for a particle
	 */
	public abstract double evaluate(double position[], FlowNetwork flowNetwork, int flowgate_position);
        

	/**
	 * Evaluates a particles 
	 * @param particle : Particle to evaluate
	 * @return Fitness function for a particle
	 */
	public double evaluate(Particle particle) {
		double position[] = particle.getPosition();
         
		double fit = evaluate(position, flowNetwork,flowgate_position);
		particle.setFitness(fit, maximize);
		return fit;
	}

	/**
	 * Is 'otherValue' better than 'fitness'?
	 * @param fitness
	 * @param otherValue
	 * @return true if 'otherValue' is better than 'fitness'
	 */
	public boolean isBetterThan(double fitness, double otherValue) {
		if (maximize) {
			if (otherValue > fitness) return true;
		} else {
			if (otherValue < fitness) return true;
		}
		return false;
	}

	/** Are we maximizing this fitness function? */
	public boolean isMaximize() {
		return maximize;
	}

	public void setMaximize(boolean maximize) {
		this.maximize = maximize;
	}

}