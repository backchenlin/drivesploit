/*
 * ModulePopup.java
 *
 * Created on May 15, 2010, 12:32:03 AM
 */
package msfgui;

import java.awt.Component;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.ParallelGroup;
import javax.swing.GroupLayout.SequentialGroup;
import javax.swing.JEditorPane;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 *
 * @author scriptjunkie
 */
public class ModulePopup extends MsfFrame implements TreeSelectionListener{
	private JMenu recentMenu;
	private String moduleType;
	private String fullName;
	private RpcConnection rpcConn;
	private String payload;
	private String target;
	private ArrayList requiredOpts; // I love how these options aren't optional
	private ArrayList optionalOpts;
	private ArrayList advancedOpts;

	/** Creates new ModulePopup from recent run */
	public ModulePopup(RpcConnection rpcConn, Object[] args, JMenu recentMenu) {
		this(args[1].toString(), rpcConn, args[0].toString(), recentMenu);
		Map opts = (Map)args[2];
		if(args[0].toString().equals("exploit")){
			//Set target
			target = opts.get("TARGET").toString();
			for (Component comp : mainPanel.getComponents()){
				if(comp instanceof JRadioButton){
					JRadioButton but = (JRadioButton)comp;
					if(but.getName().equals("targetsButton"+target)){
						but.setSelected(true);
						break;
					}
				}
			}
			//Set payload
			showPayloads(rpcConn, fullName, target, opts.get("PAYLOAD").toString());
		}
		//Set options
		for(Component comp : mainPanel.getComponents()){
			if(!(comp instanceof JTextField))
				continue;
			JTextField optionField = (JTextField)comp;
			Object optionVal = opts.get(optionField.getName().substring("field".length()));
			if(optionVal != null)
				optionField.setText(optionVal.toString());
		}
	}
	/** Creates new  ModulePopup */
	public ModulePopup(String fullName, RpcConnection rpcConn, String moduleType, JMenu recentMenu) {
		this.recentMenu = recentMenu;
		initComponents();
		exploitButton.setText("Run "+moduleType);
		exploitButton1.setText("Run "+moduleType);
		this.moduleType = moduleType;
		requiredOpts = new ArrayList();
		optionalOpts = new ArrayList();
		advancedOpts = new ArrayList();
		this.fullName = fullName;
		setTitle(fullName);
		payload = "";
		this.rpcConn = rpcConn;
		showModuleInfo(rpcConn, fullName);
		if(moduleType.equals("exploit")){
			payloadTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
			//Listen for when the selection changes.
			payloadTree.addTreeSelectionListener(this);
			payloadTree.setToggleClickCount(1);
		} else {
			mainPanel.remove(payloadScrollPane);
			mainPanel.remove(targetsLabel);
		}
		descriptionBox.setFont(authorsLabel.getFont());
		descriptionBox.setBackground(authorsLabel.getBackground());
		mainScrollPane.getVerticalScrollBar().setUnitIncrement(40);
	}

	/** Sets selected payload to the official payload */
	public void valueChanged(TreeSelectionEvent e) {
		DefaultMutableTreeNode node = (DefaultMutableTreeNode)payloadTree.getLastSelectedPathComponent();
		if (node == null || !node.isLeaf())
			return;
		payload = node.getUserObject().toString();
		while(node.getParent() != node.getRoot()){
			node = (DefaultMutableTreeNode)node.getParent();
			payload = node.getUserObject() + "/" + payload;
		}
		showOptions();
	}

   /** Displays targetsMap on frame */
	private void showModuleInfo(final RpcConnection rpcConn, final String fullName) throws HeadlessException {
		try { //Get info
			Map info = (Map) rpcConn.execute("module.info", new Object[]{moduleType, fullName});
			//Basic info
			setTitle(info.get("name") + " " + fullName);
			titleLabel.setText("<html><h2>"+info.get("name")+ "</h2></html>");
			Object references = info.get("references");
			StringBuilder referenceString = new StringBuilder();
			if(references != null){
				Object[] refArray = (Object[])references;
				referenceString.append("<br>References:<br>");
				for(int i = 0; i < refArray.length; i++){
					Object[] ref = (Object[])refArray[i];
					referenceString.append(ref[0]+": "+ref[1]+"<br> ");
				}
				referenceString.append("<br>");
			}
			descriptionBox.setText("<html><b>Description</b> "+info.get("description").toString().replaceAll("\\s+", " ")+referenceString+"</html>");
			if(info.get("license") instanceof String){
				licenseLabel.setText("<html><b>License:</b> "+ info.get("license")+"</html>");
			}else{
				Object [] license = (Object[]) info.get("license");
				StringBuilder licenseString = new StringBuilder();
				for(int i = 0; i < license.length; i++)
					licenseString.append(license[i]+" ");
				licenseLabel.setText("<html><b>License:</b> "+ licenseString+"</html>");
			}
			versionLabel.setText("<html><b>Version:</b> "+ info.get("version")+"</html>");
			//Authors
			Object[] authors = (Object[]) info.get("authors");
			StringBuilder authorLine = new StringBuilder();
			if (authors.length > 0) 
				authorLine.append(authors[0].toString());
			for (int i = 1; i < authors.length; i++) 
				authorLine.append(", " + authors[i]);
			authorsLabel.setText("<html><b>Authors:</b> "+ authorLine.toString()+"</html>");
			if(moduleType.equals("exploit")){
				//Targets
				Map targetsMap = (Map) info.get("targets");
				if(targetsMap == null){
					JOptionPane.showMessageDialog(this, "No Targets. ??");
				}else{
					String defaultTarget="";
					if(info.get("default_target") != null)
						defaultTarget = info.get("default_target").toString();
					for (Object targetName : targetsMap.keySet()) {
						JRadioButton radio = new JRadioButton();
						buttonGroup.add(radio);
						radio.setText(targetsMap.get(targetName).toString()); // NOI18N
						radio.setName("targetButton" + targetName); // NOI18N
						radio.setActionCommand(targetName.toString());
						radio.addActionListener(new ActionListener(){
							public void actionPerformed(ActionEvent e) {
								target = buttonGroup.getSelection().getActionCommand();
								showPayloads(rpcConn,fullName,target);
							}
						});
						mainPanel.add(radio);
						if (targetName.equals(defaultTarget)) {
							radio.setSelected(true);
							showPayloads(rpcConn,fullName,targetName.toString());
						}
					}
				}
			} else { //AUXILIARY
				showOptions();
			}
		} catch (MsfException ex) {
			JOptionPane.showMessageDialog(rootPane, ex);
		}
	   reGroup();
	}

   /** Creates payload menu. */
	private void showPayloads(RpcConnection rpcConn, String modName, String target) throws HeadlessException {
		showPayloads(rpcConn, modName, target, null);
	}
   /** Creates payload menu. */
	private void showPayloads(RpcConnection rpcConn, String modName, String target, String defaultPayload) throws HeadlessException {
		try { //Get info
			Object[] mlist;
			try {
				mlist = (Object[])((Map)rpcConn.execute("module.compatible_payloads",
					new Object[]{modName,target})).get("payloads"); //WARNING - ALTERED COMPATIBLE PAYLOAD RPC DEF
			} catch (MsfException ex) {
				mlist = (Object[])((Map)rpcConn.execute("module.compatible_payloads",
					new Object[]{modName})).get("payloads"); //old rpc payload def
			}
			//Ok. it worked. now replace the payload list

			DefaultTreeModel payloadModel = (DefaultTreeModel)payloadTree.getModel();
			DefaultMutableTreeNode top = (DefaultMutableTreeNode)(payloadModel.getRoot());
			int count = top.getChildCount();
			for(int i = count - 1; i >=0; i--){
				payloadModel.removeNodeFromParent((DefaultMutableTreeNode)top.getChildAt(i));
			}
			top.removeAllChildren();

			DefaultMutableTreeNode defaultPayloadNode = null;
			for (Object payloadFullName : mlist) {
				String[] names = payloadFullName.toString().split("/");
				DefaultMutableTreeNode currentNode = top;
				for (int i = 0; i < names.length; i++) {
					boolean found = false;
					for(int j = 0; j < currentNode.getChildCount(); j++){
						DefaultMutableTreeNode node = (DefaultMutableTreeNode)currentNode.getChildAt(j);
						if(node.getUserObject().toString().equals(names[i])){
							if (i < names.length - 1) 
								currentNode = node;
							found = true;
							break;
						}
					}
					if (found)
						continue;
					DefaultMutableTreeNode nod = new DefaultMutableTreeNode(names[i]);
					payloadModel.insertNodeInto(nod, currentNode, 0);
					if (i < names.length - 1) {
						payloadTree.scrollPathToVisible(new TreePath(nod.getPath()));
						currentNode = nod;
					}
					if(payloadFullName.equals(defaultPayload))
						defaultPayloadNode=nod;
				}//end for each subname
			}//end for each module
			if(defaultPayloadNode != null){
				payloadTree.scrollPathToVisible(new TreePath(defaultPayloadNode.getPath()));
				payloadTree.setSelectionPath(new TreePath(defaultPayloadNode.getPath()));
			}
			payloadTree.setRootVisible(false);
			payloadTree.revalidate();
		} catch (MsfException ex) {
			JOptionPane.showMessageDialog(rootPane, ex);
		}
	}

   /** Displays exploit and payload options. */
	private void showOptions() {
		for(Object o : requiredOpts)
			mainPanel.remove((Component)o);
		requiredOpts.clear();
		for(Object o : optionalOpts)
			mainPanel.remove((Component)o);
		optionalOpts.clear();
		for(Object o : advancedOpts)
			mainPanel.remove((Component)o);
		advancedOpts.clear();
		try{
			//display options
			Map options = (Map) rpcConn.execute("module.options", new Object[]{moduleType, fullName});
			// payload options
			if(moduleType.equals("exploit")){
				if(payload.length() <= 0){
					JOptionPane.showMessageDialog(this, "You must select a payload.");
					return;
				}
				options.putAll((Map) rpcConn.execute("module.options", new Object[]{"payload", payload.toString()}));
			}

			for (Object optionName : options.keySet()) {
				Map option = (Map)options.get(optionName); //{desc=blah, evasion=fals, advanced=false, required=true, type=port, default=blah}
				JScrollPane containerPane = new JScrollPane();
				containerPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

				JEditorPane tempText = new JEditorPane();
				tempText.setContentType("text/html");
				tempText.setEditable(false);
				tempText.setText("<html><b>"+optionName.toString()+"</b> " + option.get("desc") + "</html>");
				containerPane.setViewportView(tempText);
				containerPane.setViewportBorder(null);
				containerPane.setBorder(null);
				tempText.setBorder(null);
				mainPanel.add(containerPane);
				tempText.setBackground(authorsLabel.getBackground());
				tempText.setFont(authorsLabel.getFont());
				JTextField optionField = new JTextField();
				if (option.get("default") != null) {
					optionField.setText(option.get("default").toString());
				} else if (optionName.equals("LHOST")){ //try to find local ip
					optionField.setText(MsfguiApp.getLocalIp());
				}
				optionField.setName("field" + optionName);
				mainPanel.add(optionField);
				if(option.get("required").equals(Boolean.TRUE)){
					requiredOpts.add(containerPane);
					requiredOpts.add(optionField);
				}else if (option.get("advanced").equals(Boolean.FALSE)){
					optionalOpts.add(containerPane);
					optionalOpts.add(optionField);
				}else{
					advancedOpts.add(containerPane);
					advancedOpts.add(optionField);
				}
			}
		} catch (MsfException ex) {
			JOptionPane.showMessageDialog(rootPane, ex);
		}
		reGroup();
	}

   /** Runs the exploit with given options and payload. Closes window if successful. */
	private void runModule() {
		try{
			//Put options into request
			HashMap hash = new HashMap();
			//Exploit only stuff
			if(moduleType.equals("exploit")){
				if(payload.length() <= 0){
					JOptionPane.showMessageDialog(rootPane, "You must select a payload");
					return;
				}
				hash.put("PAYLOAD",payload.toString());
				target = buttonGroup.getSelection().getActionCommand();
				hash.put("TARGET",target);
			}
			for(Component comp : mainPanel.getComponents()){
				if(!(comp instanceof JTextField))
					continue;
				JTextField optionField = (JTextField)comp;
				if(optionField.getText().length() > 0)
					hash.put(optionField.getName().substring("field".length()), optionField.getText());
			}
			//Execute and get results
			final Object[] args =  new Object[]{moduleType, fullName,hash};
			Map info = (Map) rpcConn.execute("module.execute",args);
			if(!info.get("result").equals("success"))
				JOptionPane.showMessageDialog(rootPane, info);
			MsfguiApp.addRecentModule(args, recentMenu, rpcConn);

			//close out
			this.setVisible(false);
			this.dispose();
		} catch (MsfException ex) {
			JOptionPane.showMessageDialog(rootPane, ex);
		}
	}
	// <editor-fold defaultstate="collapsed" desc="comment">
/* DELETEME!
	private void wrapLabelText(JLabel label, String text) {
	FontMetrics fm = label.getFontMetrics(label.getFont());
	Container container = label.getParent().getParent();
	int containerWidth = container.getWidth();

	BreakIterator boundary = BreakIterator.getWordInstance();
	boundary.setText(text);

	StringBuffer trial = new StringBuffer();
	StringBuffer real = new StringBuffer("<html>");

	int start = boundary.first();
	for (int end = boundary.next(); end != BreakIterator.DONE;
	start = end, end = boundary.next()) {
	String word = text.substring(start, end);
	trial.append(word);
	int trialWidth = SwingUtilities.computeStringWidth(fm, trial.toString());
	if (trialWidth > containerWidth) {
	trial = new StringBuffer(word);
	real.append("<br>");
	}
	real.append(word);
	}
	real.append("</html>");

	label.setText(real.toString());
	} */// </editor-fold>

   /** Reformats the view based on visible options and targetsMap. */
	private void reGroup(){
		GroupLayout mainPanelLayout = (GroupLayout)mainPanel.getLayout();
		ParallelGroup horizGroup = mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
			.addComponent(titleLabel)
			.addComponent(descriptionPane, javax.swing.GroupLayout.DEFAULT_SIZE, 526, Short.MAX_VALUE)
			.addComponent(authorsLabel)
			.addComponent(licenseLabel)
			.addComponent(versionLabel);
			//Exploit only stuff
			if(moduleType.equals("exploit")){
				horizGroup.addComponent(targetsLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 431, javax.swing.GroupLayout.PREFERRED_SIZE)
					.addComponent(payloadScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 529, Short.MAX_VALUE);
			}
		horizGroup.addComponent(requiredLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
			.addComponent(optionalLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE);
		Enumeration targets = buttonGroup.getElements();
		while(targets.hasMoreElements())
			horizGroup = horizGroup.addComponent((JRadioButton)targets.nextElement());
		for(Object obj : requiredOpts)
			horizGroup = horizGroup.addComponent((Component) obj, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE);
		for(Object obj : optionalOpts)
			horizGroup = horizGroup.addComponent((Component) obj, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE);
		for(Object obj : advancedOpts)
			horizGroup = horizGroup.addComponent((Component) obj, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE);
		
		horizGroup = horizGroup.addComponent(exploitButton)
				.addComponent(exploitButton1);
		mainPanelLayout.setHorizontalGroup(mainPanelLayout.createSequentialGroup().addContainerGap()
				.addGroup(horizGroup).addContainerGap());

		SequentialGroup vGroup = mainPanelLayout.createSequentialGroup()
				.addContainerGap()
				.addComponent(titleLabel)
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
				.addComponent(descriptionPane, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
				.addComponent(authorsLabel)
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
				.addComponent(licenseLabel)
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
				.addComponent(versionLabel)
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED);
		//Exploit only stuff
		if(moduleType.equals("exploit")){
			vGroup.addComponent(targetsLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED);
			targets = buttonGroup.getElements();
			while(targets.hasMoreElements()){
				vGroup.addComponent((JRadioButton)targets.nextElement())
					.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED);
			}
			vGroup = vGroup.addComponent(payloadScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 296, javax.swing.GroupLayout.PREFERRED_SIZE)
					.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED);
		}
		boolean odd = false;
		odd = addObjectsToVgroup(vGroup, odd, requiredLabel, requiredOpts);
		vGroup = vGroup.addComponent(exploitButton1)
				.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED);
		odd = addObjectsToVgroup(vGroup, odd, optionalLabel, optionalOpts);
		odd = addObjectsToVgroup(vGroup, odd, advancedLabel, advancedOpts);
		vGroup = vGroup.addComponent(exploitButton)
				.addContainerGap();
		mainPanelLayout.setVerticalGroup(vGroup);
	}

	//helper for grouping
	private boolean addObjectsToVgroup(SequentialGroup vGroup, boolean odd, Component label, ArrayList opts) {
		vGroup = vGroup.addComponent(label, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
			.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED);
		for (Object obj : opts) {
			vGroup.addComponent((Component) obj, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE);
			if (odd) 
				vGroup.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED);
			odd = !odd;
		}
		return odd;
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
	@SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup = new javax.swing.ButtonGroup();
        mainScrollPane = new javax.swing.JScrollPane();
        mainPanel = new javax.swing.JPanel();
        titleLabel = new javax.swing.JLabel();
        authorsLabel = new javax.swing.JLabel();
        licenseLabel = new javax.swing.JLabel();
        versionLabel = new javax.swing.JLabel();
        targetsLabel = new javax.swing.JLabel();
        payloadScrollPane = new javax.swing.JScrollPane();
        payloadTree = new javax.swing.JTree();
        exploitButton = new javax.swing.JButton();
        requiredLabel = new javax.swing.JLabel();
        optionalLabel = new javax.swing.JLabel();
        descriptionPane = new javax.swing.JScrollPane();
        descriptionBox = new javax.swing.JEditorPane();
        advancedLabel = new javax.swing.JLabel();
        exploitButton1 = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        mainScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        mainScrollPane.setName("mainScrollPane"); // NOI18N
        mainScrollPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                mainScrollPaneComponentResized(evt);
            }
        });

        mainPanel.setName("mainPanel"); // NOI18N

        org.jdesktop.application.ResourceMap resourceMap = org.jdesktop.application.Application.getInstance(msfgui.MsfguiApp.class).getContext().getResourceMap(ModulePopup.class);
        titleLabel.setText(resourceMap.getString("titleLabel.text")); // NOI18N
        titleLabel.setName("titleLabel"); // NOI18N

        authorsLabel.setText(resourceMap.getString("authorsLabel.text")); // NOI18N
        authorsLabel.setName("authorsLabel"); // NOI18N

        licenseLabel.setText(resourceMap.getString("licenseLabel.text")); // NOI18N
        licenseLabel.setName("licenseLabel"); // NOI18N

        versionLabel.setText(resourceMap.getString("versionLabel.text")); // NOI18N
        versionLabel.setName("versionLabel"); // NOI18N

        targetsLabel.setText(resourceMap.getString("targetsLabel.text")); // NOI18N
        targetsLabel.setName("targetsLabel"); // NOI18N

        payloadScrollPane.setName("payloadScrollPane"); // NOI18N

        javax.swing.tree.DefaultMutableTreeNode treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("Payloads");
        payloadTree.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
        payloadTree.setName("payloadTree"); // NOI18N
        payloadScrollPane.setViewportView(payloadTree);

        exploitButton.setText(resourceMap.getString("exploitButton.text")); // NOI18N
        exploitButton.setName("exploitButton"); // NOI18N
        exploitButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exploitButtonActionPerformed(evt);
            }
        });

        requiredLabel.setText(resourceMap.getString("requiredLabel.text")); // NOI18N
        requiredLabel.setName("requiredLabel"); // NOI18N

        optionalLabel.setText(resourceMap.getString("optionalLabel.text")); // NOI18N
        optionalLabel.setName("optionalLabel"); // NOI18N

        descriptionPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        descriptionPane.setName("descriptionPane"); // NOI18N

        descriptionBox.setContentType("text/html"); // NOI18N
        descriptionBox.setEditable(false);
        descriptionBox.setName("descriptionBox"); // NOI18N
        descriptionPane.setViewportView(descriptionBox);

        advancedLabel.setText(resourceMap.getString("advancedLabel.text")); // NOI18N
        advancedLabel.setName("advancedLabel"); // NOI18N

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("msfgui/resources/ModulePopup"); // NOI18N
        exploitButton1.setText(bundle.getString("exploitButton.text")); // NOI18N
        exploitButton1.setName("exploitButton1"); // NOI18N
        exploitButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exploitButton1ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout mainPanelLayout = new javax.swing.GroupLayout(mainPanel);
        mainPanel.setLayout(mainPanelLayout);
        mainPanelLayout.setHorizontalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(descriptionPane, javax.swing.GroupLayout.DEFAULT_SIZE, 906, Short.MAX_VALUE)
                    .addComponent(authorsLabel)
                    .addComponent(licenseLabel)
                    .addComponent(versionLabel)
                    .addComponent(targetsLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 431, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(payloadScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 906, Short.MAX_VALUE)
                    .addComponent(titleLabel)
                    .addComponent(requiredLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(optionalLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(advancedLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exploitButton)
                    .addComponent(exploitButton1))
                .addContainerGap())
        );
        mainPanelLayout.setVerticalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(titleLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(descriptionPane, javax.swing.GroupLayout.PREFERRED_SIZE, 122, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(authorsLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(licenseLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(versionLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(targetsLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(payloadScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 296, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(requiredLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(optionalLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(advancedLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(exploitButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(exploitButton1)
                .addContainerGap(95, Short.MAX_VALUE))
        );

        mainScrollPane.setViewportView(mainPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(mainScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 919, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(mainScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 884, Short.MAX_VALUE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

	private void exploitButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exploitButtonActionPerformed
		runModule();
	}//GEN-LAST:event_exploitButtonActionPerformed

	private void mainScrollPaneComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_mainScrollPaneComponentResized
		//JOptionPane.showMessageDialog(rootPane, "scrollpane size: "+mainScrollPane.getWidth()+" panel size "+mainPanel.getWidth());
	}//GEN-LAST:event_mainScrollPaneComponentResized

	private void exploitButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exploitButton1ActionPerformed
		exploitButtonActionPerformed(evt);
	}//GEN-LAST:event_exploitButton1ActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel advancedLabel;
    private javax.swing.JLabel authorsLabel;
    private javax.swing.ButtonGroup buttonGroup;
    private javax.swing.JEditorPane descriptionBox;
    private javax.swing.JScrollPane descriptionPane;
    private javax.swing.JButton exploitButton;
    private javax.swing.JButton exploitButton1;
    private javax.swing.JLabel licenseLabel;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JScrollPane mainScrollPane;
    private javax.swing.JLabel optionalLabel;
    private javax.swing.JScrollPane payloadScrollPane;
    private javax.swing.JTree payloadTree;
    private javax.swing.JLabel requiredLabel;
    private javax.swing.JLabel targetsLabel;
    private javax.swing.JLabel titleLabel;
    private javax.swing.JLabel versionLabel;
    // End of variables declaration//GEN-END:variables
}
