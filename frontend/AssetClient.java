package org.fisco.bcos.asset.client;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.fisco.bcos.asset.contract.Asset;
import org.fisco.bcos.asset.contract.Asset.Register_account_eventEventResponse;
import org.fisco.bcos.asset.contract.Asset.Create_bill_eventEventResponse;
import org.fisco.bcos.asset.contract.Asset.Remove_bill_eventEventResponse;
import org.fisco.bcos.channel.client.Service;
import org.fisco.bcos.web3j.crypto.Credentials;
import org.fisco.bcos.web3j.crypto.Keys;
import org.fisco.bcos.web3j.protocol.Web3j;
import org.fisco.bcos.web3j.protocol.channel.ChannelEthereumService;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.fisco.bcos.web3j.tuples.generated.Tuple2;
import org.fisco.bcos.web3j.tx.gas.StaticGasProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

public class AssetClient {

	static Logger logger = LoggerFactory.getLogger(AssetClient.class);

	private Web3j web3j;

	private Credentials credentials;

	public Web3j getWeb3j() {
		return web3j;
	}

	public void setWeb3j(Web3j web3j) {
		this.web3j = web3j;
	}

	public Credentials getCredentials() {
		return credentials;
	}

	public void setCredentials(Credentials credentials) {
		this.credentials = credentials;
	}

	public void recordAssetAddr(String address) throws FileNotFoundException, IOException {
		Properties prop = new Properties();
		prop.setProperty("address", address);
		final Resource contractResource = new ClassPathResource("contract.properties");
		FileOutputStream fileOutputStream = new FileOutputStream(contractResource.getFile());
		prop.store(fileOutputStream, "contract address");
	}

	public String loadAssetAddr() throws Exception {
		// load Asset contact address from contract.properties
		Properties prop = new Properties();
		final Resource contractResource = new ClassPathResource("contract.properties");
		prop.load(contractResource.getInputStream());

		String contractAddress = prop.getProperty("address");
		if (contractAddress == null || contractAddress.trim().equals("")) {
			throw new Exception(" load Asset contract address failed, please deploy it first. ");
		}
		logger.info(" load Asset address from contract.properties, address is {}", contractAddress);
		return contractAddress;
	}

	public void initialize() throws Exception {

		// init the Service
		@SuppressWarnings("resource")
		ApplicationContext context = new ClassPathXmlApplicationContext("classpath:applicationContext.xml");
		Service service = context.getBean(Service.class);
		service.run();

		ChannelEthereumService channelEthereumService = new ChannelEthereumService();
		channelEthereumService.setChannelService(service);
		Web3j web3j = Web3j.build(channelEthereumService, 1);

		// init Credentials
		Credentials credentials = Credentials.create(Keys.createEcKeyPair());

		setCredentials(credentials);
		setWeb3j(web3j);

		logger.debug(" web3j is " + web3j + " ,credentials is " + credentials);
	}

	private static BigInteger gasPrice = new BigInteger("30000000");
	private static BigInteger gasLimit = new BigInteger("30000000");

	public void deployAssetAndRecordAddr() {

		try {
			Asset asset = Asset.deploy(web3j, credentials, new StaticGasProvider(gasPrice, gasLimit)).send();
			System.out.println(" deploy Asset success, contract address is " + asset.getContractAddress());

			recordAssetAddr(asset.getContractAddress());
		} catch (Exception e) {
			System.out.println(" deploy Asset contract failed, error message is  " + e.getMessage());
		}
	}

	public void show_balances(String account) {
		try{
	 		String contractAddress = loadAssetAddr();
	 		Asset asset = Asset.load(contractAddress, web3j, credentials, new StaticGasProvider(gasPrice, gasLimit));
	 		Tuple2<BigInteger, BigInteger> result = asset.show_balances(account).send();
			if (result.getValue1().compareTo(new BigInteger("0")) == 0) {
				System.out.printf(" show_balances succeed\n account => %s\n balances => %s\n", account, result.getValue2());
			} else {
				System.out.printf(" account %s is not exist \n", account);
			}
		} catch (Exception e) {
	 		System.out.printf(" show_balances failed, error message is %s\n", e.getMessage());
		}
	}

	public void register_account(String account, BigInteger amount) {
		try {
			String contractAddress = loadAssetAddr();
			Asset asset = Asset.load(contractAddress, web3j, credentials, new StaticGasProvider(gasPrice, gasLimit));
			TransactionReceipt receipt = asset.register_account(account, amount).send();
			List<Register_account_eventEventResponse> response = asset.getRegister_account_eventEvents(receipt);
			if (!response.isEmpty()) {
				if (response.get(0).ret_code.compareTo(new BigInteger("0")) == 0) {
					System.out.printf(" register_account success\n account => %s\n balances => %s\n", account, amount);
				} else if (response.get(0).ret_code.compareTo(new BigInteger("-1")) == 0) {
					System.out.printf(" register_account failed, the account is already existed\n");
				} else if (response.get(0).ret_code.compareTo(new BigInteger("-1")) == 0) {
					System.out.printf(" register_account failed because unknown error\n");
				}
			} else {
				System.out.println(" event log not found, maybe transaction not exec. ");
			}
		} catch (Exception e) {
			System.out.printf(" register asset account failed, error message is %s\n", e.getMessage());
		}
	}

	public void show_bill(String lender, String borrower){
		try{
	 		String contractAddress = loadAssetAddr();
	 		Asset asset = Asset.load(contractAddress, web3j, credentials, new StaticGasProvider(gasPrice, gasLimit));
	 		Tuple2<BigInteger, BigInteger> result = asset.show_bill(lender, borrower).send();
			if (result.getValue1().compareTo(new BigInteger("0")) == 0) {
				System.out.printf(" show_bill succeed, amount = %s\n", result.getValue2());
			} else if (result.getValue1().compareTo(new BigInteger("-1")) == 0) {
				System.out.printf(" show_bill failed, account doesn't exist\n");
			} else if (result.getValue1().compareTo(new BigInteger("-2")) == 0) {
				System.out.printf(" show_bill failed, bill doesn't exist\n");
			}
		} catch (Exception e) {
	 		System.out.printf(" show_bill failed, error message is %s\n", e.getMessage());
		}
	}

	public void create_bill(String lender, String borrower, BigInteger amount){
		try {
			String contractAddress = loadAssetAddr();
			Asset asset = Asset.load(contractAddress, web3j, credentials, new StaticGasProvider(gasPrice, gasLimit));
			TransactionReceipt receipt = asset.create_bill(lender, borrower, amount).send();
			List<Create_bill_eventEventResponse> response = asset.getCreate_bill_eventEvents(receipt);
			if (!response.isEmpty()) {
				if (response.get(0).ret_code.compareTo(new BigInteger("0")) == 0) {
					System.out.printf(" create_bill success\n");
				} else {
					System.out.printf(" create_bill failed, account doesn't exist\n");
				}
			} else {
				System.out.println(" event log not found, maybe transaction not exec. ");
			}
		} catch (Exception e) {
			System.out.printf(" register asset account failed, error message is %s\n", e.getMessage());
		}
	}

	public void remove_bill(String lender, String borrower){
		try {
			String contractAddress = loadAssetAddr();
			Asset asset = Asset.load(contractAddress, web3j, credentials, new StaticGasProvider(gasPrice, gasLimit));
			TransactionReceipt receipt = asset.remove_bill(lender, borrower).send();
			List<Remove_bill_eventEventResponse> response = asset.getRemove_bill_eventEvents(receipt);
			if (!response.isEmpty()) {
				if (response.get(0).ret_code.compareTo(new BigInteger("0")) == 0) {
					System.out.printf(" remove_bill success\n");
				} else if (response.get(0).ret_code.compareTo(new BigInteger("-1")) == 0) {
					System.out.printf(" show_bill failed, account doesn't exist\n");
				} else if (response.get(0).ret_code.compareTo(new BigInteger("-2")) == 0) {
					System.out.printf(" show_bill failed, bill doesn't exist\n");
				} else if (response.get(0).ret_code.compareTo(new BigInteger("-3")) == 0) {
					System.out.printf(" show_bill failed, account %s balances don't enough\n", borrower);
				}
			} else {
				System.out.println(" event log not found, maybe transaction not exec. ");
			}
		} catch (Exception e) {
			System.out.printf(" register asset account failed, error message is %s\n", e.getMessage());
		}
	}

	public static void Usage() {
		System.out.println("AssetClient Usage:\n");
		System.out.println("deploy\n");
		System.out.println("show_balances    account\n");
		System.out.println("register_account account amount\n");
		System.out.println("show_bill        lender  borrower\n");
		System.out.println("create_bill      lender  borrower amount\n");
		System.out.println("remove_bill      lender  borrower\n");
	}

	public static void main(String[] args) throws Exception {

		if (args.length < 1) {
			Usage();
		}

		AssetClient client = new AssetClient();
		client.initialize();

		switch (args[0]) {
		case "deploy":
			client.deployAssetAndRecordAddr();
			break;
		case "show_balances":
			if (args.length < 2) {
				Usage();
			}
			client.show_balances(args[1]);
			break;
		case "register_account":
			if (args.length < 3) {
				Usage();
			}
			client.register_account(args[1], new BigInteger(args[2]));
			break;
		case "show_bill":
			if (args.length < 3) {
				Usage();
			}
			client.show_bill(args[1], args[2]);
			break;
		case "create_bill":
			if (args.length < 4) {
				Usage();
			}
			client.create_bill(args[1], args[2], new BigInteger(args[3]));
			break;
		case "remove_bill":
			if (args.length < 3) {
				Usage();
			}
			client.remove_bill(args[1], args[2]);
			break;
		default: 
			Usage();
		}

		System.exit(0);
	}
}
