package es.us.isa.Choco.fmdiag.configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import choco.Choco;
import choco.cp.model.CPModel;
import choco.cp.solver.CPSolver;
import choco.kernel.model.Model;
import choco.kernel.model.constraints.Constraint;
import choco.kernel.model.variables.integer.IntegerVariable;
import choco.kernel.solver.Solver;
import es.us.isa.ChocoReasoner.ChocoQuestion;
import es.us.isa.ChocoReasoner.ChocoReasoner;
import es.us.isa.ChocoReasoner.ChocoResult;
import es.us.isa.FAMA.Benchmarking.PerformanceResult;
import es.us.isa.FAMA.Exceptions.FAMAException;
import es.us.isa.FAMA.Reasoner.Reasoner;
import es.us.isa.FAMA.models.featureModel.Product;
import es.us.isa.FAMA.models.variabilityModel.VariabilityElement;
import es.us.isa.FAMA.stagedConfigManager.Configuration;

public class ChocoMaxConfigurationFMDIAGMutX extends ChocoQuestion {

	// This should be a full configuration
	public Configuration configuration;

	// Configuration Constraints
	private Map<String, Constraint> configurationConstraints = new HashMap<String, Constraint>();
	private Map<String, Constraint> selectedConstraints = new HashMap<String, Constraint>();
	private Map<String, Constraint> deselectedConstraints = new HashMap<String, Constraint>();

	// Model Constraints
	private Map<String, Constraint> modelConstraints = new HashMap<String, Constraint>();
	
	// All Constraints
	private Map<String, Constraint> constraints = new HashMap<String, Constraint>();

	// All Variables
	private IntegerVariable[] variables;
	
	// Result
	public Map<String, Constraint> result = new HashMap<String, Constraint>();

	Product s;

	public Set<String> getResultConstraints() {
		return result.keySet();
	}

	public void setConfiguration(Product s) {
		System.out.println(s);
		this.s=s;
	}

	public void preAnswer(Reasoner r) {
		// Generate all configuration constraints
		
		System.out.println("Conf: " + configuration.getElements().entrySet());
		for (Entry<VariabilityElement, Integer> e : configuration.getElements().entrySet()) {
			IntegerVariable var = ((ChocoReasoner) r).getVariables().get(e.getKey().getName());
			String name = "C_"+e.getKey().getName();
			if (e.getValue() > 0) {
				configurationConstraints.put(name, Choco.eq(var, 1));
				selectedConstraints.put(name, Choco.eq(var, 1));
			} else if (e.getValue() == 0) {
				configurationConstraints.put(name, Choco.eq(var, 0));
				deselectedConstraints.put(name, Choco.eq(var, 0));
			}
		}
		
//		System.out.println("#Constraints: " + configurationConstraints.size());
		// Get all model constraints
		modelConstraints.putAll(((ChocoReasoner) r).getRelations());
		
		// Add all Constraints
		constraints.putAll(modelConstraints);
		constraints.putAll(configurationConstraints);

		
		// Get all model variables
		variables = ((ChocoReasoner) r).getVars();
	}

	//
	public PerformanceResult answer(Reasoner r) throws FAMAException {
		//Basic data
		ArrayList<String> S = new ArrayList<String>();
		ArrayList<String> AC = new ArrayList<String>();

//		System.out.println("###" + configuration.getElements().size());
		
		//Instantiating it for configuration extension
		AC.addAll(modelConstraints.keySet());
		AC.addAll(configurationConstraints.keySet());
		
		//Instantiating it for configuration extension
		S.addAll(deselectedConstraints.keySet());
		
		System.out.println(AC + " - " + S);
		//Auxiliary data
		List<String> fmdiag = fmdiag(S, AC);
	
		for (String s : fmdiag) {
			result.put(s, deselectedConstraints.get(s));
		}
		
		return new ChocoResult();
	}
	
/*	public Product getProduct() {
		return prod;
	}
*/
	public List<String> fmdiag(List<String> S, List<String> AC) {
		System.out.println("AC:     " + AC + " - " + isConsistent(AC));
		System.out.println("(AC-S): " + less(AC,S) + " - " + isConsistent(less(AC,S)) + "\n");
	
		if (S.size() == 0 || !isConsistent(less(AC, S)) || isConsistent(AC)) {
			return new ArrayList<String>();
		} else {
			List<String> res = diag(new ArrayList<String>(), S, AC);
			
			return res;
		}
	}

	Integer ccN = 0;
	public List<String> diag(List<String> D, List<String> S, List<String> AC) {		
			System.out.println("D: " + D + "\nS: " + S + "\nAC: " + AC + "\n");
			
			if (D.size() != 0 && isConsistent(AC)) {
				//	System.out.println("");
					return new ArrayList<String>();
			}		
		
			if (S.size() == 1) {
				//	System.out.println("");
					return S;
				}
				
			int k = S.size() / 2;
			List<String> S1 = S.subList(0, k);
			List<String> S2 = S.subList(k, S.size());
		//	System.out.println("S1: " + S1 + "\nS2: " + S2 + "\n");
			List<String> A1 = diag(S2, S1, less(AC, S2));
			List<String> A2 = diag(A1, S2, less(AC, A1));
			//System.out.println(actual + " -- R " + plus(A1, A2)); 
			return plus(A1, A2);
		}

	private List<String> plus(List<String> a1, List<String> a2) {
		List<String> res = new ArrayList<String>();
		res.addAll(a1);
		res.addAll(a2);
		return res;
	}

	private List<String> less(List<String> aC, List<String> s2) {
		List<String> res = new ArrayList<String>();
		res.addAll(aC);
		res.removeAll(s2);
		return res;
	}

	public boolean isConsistent(Collection<String> aC) {
		Model p = new CPModel();
		p.addVariables(variables);

		for (String rel : aC) {
			Constraint c = constraints.get(rel);
			try {
				p.addConstraint(c);
			}catch (NullPointerException e) {
				System.err.println(rel);
			}
		}
		Solver s = new CPSolver();
		s.read(p);
		s.solve();
		return s.isFeasible();
	}
}