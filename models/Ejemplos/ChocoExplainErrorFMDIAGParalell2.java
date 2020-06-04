package es.us.isa.Choco.fmdiag.configuration;

import static choco.Choco.eq;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import choco.Choco;
import choco.cp.model.CPModel;
import choco.cp.solver.CPSolver;
import choco.kernel.model.Model;
import choco.kernel.model.constraints.Constraint;
import choco.kernel.model.variables.integer.IntegerExpressionVariable;
import choco.kernel.model.variables.integer.IntegerVariable;
import choco.kernel.solver.Solver;
import es.us.isa.ChocoReasoner.ChocoQuestion;
import es.us.isa.ChocoReasoner.ChocoReasoner;
import es.us.isa.ChocoReasoner.ChocoResult;
import es.us.isa.FAMA.Benchmarking.PerformanceResult;
import es.us.isa.FAMA.Exceptions.FAMAException;
import es.us.isa.FAMA.Reasoner.Reasoner;
import es.us.isa.FAMA.Reasoner.questions.ExplainErrorsQuestion;
import es.us.isa.FAMA.Reasoner.questions.ValidConfigurationErrorsQuestion;
import es.us.isa.FAMA.errors.Error;
import es.us.isa.FAMA.errors.Observation;
import es.us.isa.FAMA.models.featureModel.GenericFeature;
import es.us.isa.FAMA.models.featureModel.Product;
import es.us.isa.FAMA.models.variabilityModel.VariabilityElement;

import java.util.Arrays;
import java.util.Collections;

public class ChocoExplainErrorFMDIAGParalell2 extends ChocoQuestion implements ValidConfigurationErrorsQuestion {

	public boolean returnAllPossibeExplanations = false;
	private ChocoReasoner chReasoner;
	public List<String> explanations;

	public Map<String, Constraint> relations = null;
	public boolean flexactive = false;
	public int m = 1;

	Product s,r;
	public Map<String, Constraint> result = new HashMap<String, Constraint>();

	public int numberOfThreads = 4;
	public int baseSize = 100;

	ExecutorService executorService = Executors.newCachedThreadPool();
	////////////For Parallel FlexDiag...

	public ChocoExplainErrorFMDIAGParalell2(int m, int t){
		this.m = m;
		this.numberOfThreads = t;
	}
	
	public void setConfiguration(Product s) {
		this.s=s;
	}

	public void setRequirement(Product r) {
		this.r=r;
	}
	
	@Override
	public void setProduct(Product p) {
		this.s = p;
	}

	@Override
	public boolean isValid() {
		return false;
	}

	public PerformanceResult answer(Reasoner r) throws FAMAException {
		ChocoResult res = new ChocoResult();
		chReasoner = (ChocoReasoner) r;

		// solve the problem y fmdiag
    	relations = new HashMap<String, Constraint>();

    	Map<String, Constraint> productConstraint = new HashMap<String, Constraint>();    	
		ArrayList<String> feats= new ArrayList<String>();
		
		for (GenericFeature f : this.s.getFeatures()) {
			IntegerVariable var = chReasoner.getVariables().get(f.getName());
			//System.out.println(var);
			String name="U_" + f.getName();
			productConstraint.put(name, Choco.eq(var, 1));
			feats.add(name);
		}
		
		///
		Map<String, Constraint> requirementConstraint = new HashMap<String, Constraint>();
		for (GenericFeature f : this.r.getFeatures()) {
			IntegerVariable var = chReasoner.getVariables().get(f.getName());
			//System.out.println(var);
			requirementConstraint.put("R_" + f.getName(), Choco.eq(var, 1));
		}

		relations.putAll(chReasoner.getRelations());
		relations.putAll(requirementConstraint);
		relations.putAll(productConstraint);
		
		ArrayList<String> S = new ArrayList<String>(feats);
		ArrayList<String> AC = new ArrayList<String>(relations.keySet());

		if (returnAllPossibeExplanations == false) {

			List<String> fmdiag = fmdiag(S, AC);

			for (String s : fmdiag) {
				result.put(s, productConstraint.get(s));
			}

		} else {
			List<String> allExpl = new LinkedList<String>();
			//HashSet<String> allExpl = new HashSet<String>();
			List<String> fmdiag = fmdiag(S, AC);

			while (fmdiag.size() != 0) {
				allExpl.addAll(fmdiag);
				S.removeAll(fmdiag);
				AC.removeAll(fmdiag);
				fmdiag = fmdiag(S, AC);
			}
			for (String s : allExpl) {
				result.put(s, productConstraint.get(s));
			}
		}

		return new ChocoResult();
	}

	public List<String> fmdiag(List<String> S, List<String> AC) {
		if (S.size() == 0 || !isConsistent(less(AC, S))) {
			return new ArrayList<String>();
		} else {
			diagThreads dt = new diagThreads(new ArrayList<String>(), S, AC, numberOfThreads, executorService);
//			diagThreads dt = new diagThreads(new ArrayList<String>(), S, AC, numberOfThreads, 100, executorService);
			Future<List<String>> submit = executorService.submit(dt);
			
			try {
				return submit.get();
			} catch (InterruptedException e) {
				e.printStackTrace();
				return new LinkedList<String>();
			} catch (ExecutionException e) {
				e.printStackTrace();
				return new LinkedList<String>();
			}
		}
	}

	public class Solution {
		    public volatile boolean flag = false;
			public volatile int step = 0;
			/////////////////////////////////////////
			public volatile List<String> ACC;
			public volatile List<Boolean> considered;
			
	 }
	 
     public final Solution foundSolution = new Solution();

	public class diagThreads implements Callable<List<String>>{
		List<String> D, S, AC;
		int numberOfSplits;
		int maxSize;
		ExecutorService executorService;
		
		public diagThreads(List<String> D, List<String> S,List<String> AC,int numberOfSplits, ExecutorService executorService){
			this.D=D;
			this.S=S;
			this.AC=AC;
			this.executorService=executorService;
			this.numberOfSplits=numberOfSplits;
			
			if (foundSolution.ACC == null){
				foundSolution.ACC = AC;
				List<Boolean> considered = new ArrayList<Boolean>(Arrays.asList(new Boolean[10]));
				Collections.fill(considered, Boolean.FALSE);
			}				
		}
		
		public diagThreads(List<String> D, List<String> S,List<String> AC, int numberOfSplits, int MS, ExecutorService executorService){
			this.D=D;
			this.S=S;
			this.AC=AC;
			this.executorService=executorService;
			this.numberOfSplits=numberOfSplits;
			
			if (foundSolution.ACC == null){
				foundSolution.ACC = AC;
				List<Boolean> considered = new ArrayList<Boolean>(Arrays.asList(new Boolean[10]));
				Collections.fill(considered, Boolean.FALSE);
			}				
			
			this.maxSize = MS;
		}

		public List<String> call() throws Exception {		
			if (D.size() != 0 && isConsistent(AC)){					
				List<String> nAC = plus(D, AC);
				
				diagThreads dt = new diagThreads(new ArrayList<String>(), D, nAC, numberOfSplits, maxSize, executorService);
				Future<List<String>> submit = executorService.submit(dt);
				return submit.get();
			}
			
			if(flexactive){
				if(S.size()<=m){
				   return S;
				}
			}else{				
				if(S.size()==1){
				   return S;
				}
			}
			
			List<List<String>> outLists= new LinkedList<List<String>>();
			//Hay una optimizacion a realizar si usamos algo m'as de memoria. Si almacenamos en un mapa los 
			//resultados que tengamos siempre podemos volver a usar D=0 como hacen en el paper
			
			int div = 0; //div: El tamaño de cada división
						
			if (S.size() >= numberOfSplits){
			   div = S.size() / numberOfSplits;
			
			   if ((S.size() % numberOfSplits)>0)
				   div++;
			}
			else //S de tamaño menor a cantidad de divisiones, entonces el tamaño de cada division sería 1
				div = 1;
			
			List<List<String>> splitListToSubLists = splitListToSubLists(S, div);

			for(List<String> s: splitListToSubLists){
				List<String> rest= getRest(s,splitListToSubLists);	
				List<String> less = less(AC,rest);
				diagThreads dt = new diagThreads(rest, s,less , numberOfSplits, executorService);
				
				Future<List<String>> submit = executorService.submit(dt);
				outLists.add(submit.get());
			}
			
			return plus(outLists);
		}

		public List<String> call2() throws Exception {		
			if (D.size() != 0 && isConsistent(AC)){					
				List<String> nAC = plus(D, AC);
				
				if (D.size() <= maxSize){
					return diag(new ArrayList<String>(), D, nAC);
					
				}else{
					diagThreads dt = new diagThreads(new ArrayList<String>(), D, nAC, numberOfSplits, maxSize, executorService);
					Future<List<String>> submit = executorService.submit(dt);
					return submit.get();
				}
			}
			
			if(flexactive){
				if(S.size()<=m){
				   return S;
				}
			}else{				
				if(S.size()==1){
				   return S;
				}
			}
			
			List<List<String>> outLists= new LinkedList<List<String>>();
			//Hay una optimizacion a realizar si usamos algo m'as de memoria. Si almacenamos en un mapa los 
			//resultados que tengamos siempre podemos volver a usar D=0 como hacen en el paper
			
			int div = 0; //div: El tamaño de cada división
						
			if (S.size() >= numberOfSplits){
			   div = S.size() / numberOfSplits;
			
			   if ((S.size() % numberOfSplits)>0)
				   div++;
			}
			else //S de tamaño menor a cantidad de divisiones, entonces el tamaño de cada division sería 1
				div = 1;
			
			List<List<String>> splitListToSubLists = splitListToSubLists(S, div);

			for(List<String> s: splitListToSubLists){
				List<String> rest= getRest(s,splitListToSubLists);	
				List<String> less = less(AC,rest);
				diagThreads dt = new diagThreads(rest, s,less , numberOfSplits, maxSize, executorService);
				
				Future<List<String>> submit = executorService.submit(dt);
				outLists.add(submit.get());
			}
			
			return plus(outLists);
		}
	
		public List<String> call3() throws Exception {		
			if (D.size() != 0 && isConsistent(AC)){					
				return new ArrayList<String>();
			}
			
			if(flexactive){
				if(S.size()<=m){
				   return S;
				}
			}else{				
				if(S.size()==1){
				   return S;
				}
			}
			
			List<List<String>> outLists= new LinkedList<List<String>>();
			//Hay una optimizacion a realizar si usamos algo m'as de memoria. Si almacenamos en un mapa los 
			//resultados que tengamos siempre podemos volver a usar D=0 como hacen en el paper
			
			int div = 0; //div: El tamaño de cada división
						
			if (S.size() >= numberOfSplits){
			   div = S.size() / numberOfSplits;
			
			   if ((S.size() % numberOfSplits)>0)
				   div++;
			}
			else //S de tamaño menor a cantidad de divisiones, entonces el tamaño de cada division sería 1
				div = 1;
			
			List<List<String>> splitListToSubLists = splitListToSubLists(S, div);

			for(List<String> s: splitListToSubLists){
				List<String> rest= getRest(s,splitListToSubLists);	
				List<String> less = less(AC,rest);
				diagThreads dt = new diagThreads(rest, s,less , numberOfSplits, executorService);
				
				Future<List<String>> submit = executorService.submit(dt);
				outLists.add(submit.get());
			}
			
			return plus(outLists);
		}
	
		public List<String> call4() throws Exception {		
			if (D.size() != 0 && isConsistent(AC)){
				return new ArrayList<String>();
			}
						
			if(flexactive){
				if(S.size()<=m){	
				   return S;
				}
			}
			else{				
				if(S.size()==1){
					return S;
				}
			}

			//Hay una optimizacion a realizar si usamos algo m'as de memoria. Si almacenamos en un mapa los 
			//resultados que tengamos siempre podemos volver a usar D=0 como hacen en el paper
			int div = 0; //div: El tamaño de cada división
			
			if (S.size() >= numberOfSplits){
			   div = S.size() / numberOfSplits;
			
			   if ((S.size() % numberOfSplits)>0)
				   div++;
			}
			else //S de tamaño menor a cantidad de divisiones, entonces el tamaño de cada division sería 1
				div = 1;

			List<List<String>> splitListToSubLists = splitListToSubLists(S, div);						
			List<List<String>> outLists = new LinkedList<List<String>>();
			List<List<String>> d = new LinkedList<List<String>>();

			int i = 0;
			for(List<String> s: splitListToSubLists){
				d.add(i,getRest(s,splitListToSubLists));					
				List<String> less = less(AC, d.get(i));
				
				//////AC sin D
				diagThreads dt = new diagThreads(d.get(i), s, less , numberOfSplits, executorService);
				Future<List<String>> submit = executorService.submit(dt);
				outLists.add(submit.get());		
				i++;
			}

			i = 0;
			for(List<String> s: splitListToSubLists){
				List<String> less = less(AC, outLists.get(i));
								
				//////AC con D
				diagThreads dt2 = new diagThreads(outLists.get(i), d.get(i), less , numberOfSplits, executorService);
				Future<List<String>> submit2 = executorService.submit(dt2);
				outLists.add(submit2.get());
				i++;
			}

			return plus(outLists);
		}
		
		private List<String> getRest(List<String> s2, List<List<String>> splitListToSubLists) {
			LinkedList<String> res= new LinkedList<String>();
			for(List<String> c:splitListToSubLists){
				if(c!=s2){
					res.addAll(c);
				}
			}
			return res;
		}

		private List<String> plus(List<String> a1, List<String> a2) {
			List<String> res = new ArrayList<String>();
			res.addAll(a1);
			res.addAll(a2);
			return res;
		}

		private List<String> plus(List<List<String>> outLists) {
			List<String> res=new ArrayList<String>();
			
			for(List<String> s:outLists){	
				res.addAll(s);
			}
			
			return res;		
		}

		public <T> List<List<T>> splitListToSubLists(List<T> parentList, int subListSize) {
			  List<List<T>> subLists = new ArrayList<List<T>>();
			  
			  if (subListSize > parentList.size()) {
			     subLists.add(parentList);
			     } 
			  else {
			     int remainingElements = parentList.size();
			     int startIndex = 0;
			     int endIndex = subListSize;
			     do {
			        List<T> subList = parentList.subList(startIndex, endIndex);
			        subLists.add(subList);
			        startIndex = endIndex;
			        if (remainingElements - subListSize >= subListSize) {
			           endIndex = startIndex + subListSize;
			        } else {
			           endIndex = startIndex + remainingElements - subList.size();
			        }
			        remainingElements -= subList.size();
			     } while (remainingElements > 0);

			  }
			  return subLists;
		}
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

	private boolean isConsistent(Collection<String> aC) {
		Model p = new CPModel();
		p.addVariables(chReasoner.getVars());

		for (String rel : aC) {
			Constraint c = relations.get(rel);
			
			if (c == null) {
				System.out.println("Error");
			}
			p.addConstraint(c);
		}
		Solver s = new CPSolver();
		s.read(p);
		s.solve();
		return s.isFeasible();
	}
	
	/////////////////////Sequential FlexDiag
	public List<String> diag(List<String> D, List<String> S, List<String> AC) {
		if (D.size() != 0 && isConsistent(AC)) {
			return new ArrayList<String>();
		}
		if (flexactive) {
			if (S.size() <= m) {
				return S;
			}
		} else {
			if (S.size() == 1) {
				return S;
			}
		}
		int k = S.size() / 2;
		List<String> S1 = S.subList(0, k);
		List<String> S2 = S.subList(k, S.size());
		List<String> A1 = diag(S2, S1, less(AC, S2));
		List<String> A2 = diag(A1, S2, less(AC, A1));
		return plus(A1, A2);
	}
}
