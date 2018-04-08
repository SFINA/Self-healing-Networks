package ObjectiveFunctionMain;

//import net.sourceforge.jswarm_pso.Particle;
import ModifiedJSwarmMain.Particle;
import network.FlowNetwork;

/**
 * Simple particle example
 *
 * @author Pablo Cingolani <pcingola@users.sourceforge.net>
 */

    public class MyParticle {

    public static Particle fixDimensions(int[] i) {
        Particle part = new Particle(i[0]);
        return part;
    }

}
