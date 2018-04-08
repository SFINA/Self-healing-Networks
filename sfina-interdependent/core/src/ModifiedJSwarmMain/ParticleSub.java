package ModifiedJSwarmMain;

import ModifiedJSwarmMain.Particle;

public class ParticleSub {
    
    public static void main(String[] arguments) {
        
        Particle part = new Particle();
        System.out.println(part.getDimension());
        part.selfFactory();
        part.allocate(part.getDimension());
        //System.out.println(greeting);
    }
}
