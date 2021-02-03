# FaMaProdConf-TestSuite (BOLON)

This repository is hosting the source code and scripts implementing for the completion of product configuration. ChocoBOLONFMDIAG.java and the executable file BOLON.jar allows the following paramenter:

* model: A CNF file depicting the constraints representing the background knowledge for the algorithm (e.g. the constraints in a feature model)
* product: A CNF file representing the product selection or user requirements to look for the confict set. 
* solver: The backend solver to use, we support at this time, CSP and FMDIAG.

Also, the following scripts are provided to execute BOLON.jar on a set of models and partial configurations.:
* execBOLON.bat 
* execBOLON2.bat

Finally, also provide:
 * A set of models with a fixed set of conflict sets. 
 * The test suite files for the results in the associated paper. 
