pragma solidity ^0.4.21;

import "./Table.sol";

contract Asset { 

	 event register_account_event(int ret_code, string indexed account, int indexed amount);
	 event create_bill_event(int ret_code);
	 event remove_bill_event(int ret_code);

	function createTable(string table_name, string key_name, string value_name) private {
		TableFactory tf = TableFactory(0x1001);
		tf.createTable(table_name, key_name, value_name);
	}

	function openTable(string table_name) private returns(Table){
		TableFactory tf = TableFactory(0x1001);
		Table table = tf.openTable(table_name);
		return table;
	}

	constructor() { 
		createTable("ACCOUNT_LIST", "account", "balances");
		// createTable("ACCOUNT_TRAVERSAL", "", "account");
	}

	function account_existance(string account) public constant returns (bool){
		Table table = openTable("ACCOUNT_LIST");
		Entries entries = table.select(account, table.newCondition());
		int balances = 0;
		if (int(entries.size()) == 0){
			return false;
		} else {
			return true;
		}
	}


	/*
	描述 ： 根据资产账户查询资产金额
	参数 ： 
			account : 资产账户
	返回值：
			参数一： 成功返回0, 账户不存在返回-1
			参数二： 第一个参数为0时有效，资产金额
	*/

	function show_balances(string account) public constant returns (int, int){
		Table table = openTable("ACCOUNT_LIST");
		Entries entries = table.select(account, table.newCondition());
		int balances = 0;
		if (int(entries.size()) == 0){
			return (int(-1), balances);
		} else {
			Entry entry = entries.get(0);
			return (0, int(entry.getInt("balances")));
		}
	}

	/*
	描述 ： 资产注册
	参数 ： 
			account : 资产账户
			amount  : 资产金额
	返回值：
			0  资产注册成功
			-1 资产账户已存在
			-2 其他错误（表格插入数据失败）
	*/
	function register_account(string account, int amount) public returns (int){ 
		int ret_code = 0;
		if (!account_existance(account)) {
			Table account_table = openTable("ACCOUNT_LIST");
			Entry entry = account_table.newEntry();
			entry.set("account", account);
			entry.set("balances", int256(amount));
			int count = account_table.insert(account, entry);
			if (count == 1) {
				// Table account_traversal_table = openTable("account_traversal_table");
				// entry = account_traversal_table.newEntry();
				// entry.set("", "");
				// entry.set("account", account);
				// account_traversal_table.insert("", entry);
				createTable(account, "borrower", "amount");
				ret_code = int(0);
			} else {
				ret_code = int(-2);
			}
		} else {
			ret_code = int(-1);
		}

		emit register_account_event(ret_code, account, amount);
		return ret_code;
	}

	/*
	描述 ： 显示单条账单记录
	参数 ： 
			account : 资产账户
			borrower  : 借款账户
	返回值：
		参数一：
			0  有此记录
			-1 无该资产账户
			-2 无该借款记录
		参数二： 借款金额
	*/
	function show_bill(string lender, string borrower) public constant returns (int, int){
		int ret_code = 0;
		int borrow_amount = 0;
		if (account_existance(lender)) {
			Table bill_table = openTable(lender);
			Entries entries = bill_table.select(borrower, bill_table.newCondition());
			if (int(entries.size()) == 0){
				ret_code = -2;
			} else {
				ret_code = 0;
				borrow_amount = entries.get(0).getInt("amount");
			}
		} else{
			ret_code = -1;
		}
		return (ret_code, borrow_amount);
	}

	/*
	描述 ： 创建应收账款单据
	参数 ： 
			borrower  : 借款账户
			lender : 放款账户
			amount ：借款金额
	返回值：
			0  成功
			-1 账户不存在
	*/
	function create_bill(string lender, string borrower, int amount) public returns (int){
		int ret_code = 0;
		if (account_existance(borrower) && account_existance(lender)) {
			Table bill_table = openTable(lender);
			Entries entries = bill_table.select(borrower, bill_table.newCondition());
			if (int(entries.size()) != 0){
				amount = amount + entries.get(0).getInt("amount");
				bill_table.remove(borrower, bill_table.newCondition());
			}
			Entry entry = bill_table.newEntry();
			entry.set("borrower", borrower);
			entry.set("amount", amount);
			bill_table.insert(borrower, entry);
			ret_code = 0;
		} else {
			ret_code = -1;
		}
		emit create_bill_event(ret_code);
		return ret_code;
	}

	/*
	描述 ： 结算单据
	参数 ： 
			borrower  : 借款账户
			lender : 放款账户
	返回值：
			0  成功
			-1 账户不存在
			-2 账款不存在
			-3 账户余额不足无法结算
	*/

	function remove_bill(string lender, string borrower) public returns (int){
		int ret_code = 0;
		if (account_existance(borrower) && account_existance(lender)) {
			Table bill_table = openTable(lender);
			Entries entries = bill_table.select(borrower, bill_table.newCondition());
			if (int(entries.size()) == 0){
				ret_code = -2;
			} else {
				Table account_table = openTable("ACCOUNT_LIST");
				int borrower_balances = account_table.select(borrower, account_table.newCondition()).get(0).getInt("balances");
				int lender_balances =  account_table.select(lender, account_table.newCondition()).get(0).getInt("balances");
				int amount = entries.get(0).getInt("amount");
				if (borrower_balances < amount){
					ret_code = -3;
				} else {
					bill_table.remove(borrower, bill_table.newCondition());
					account_table.remove(borrower, account_table.newCondition());
					account_table.remove(lender, account_table.newCondition());
					Entry entry_borrower = account_table.newEntry();
					Entry entry_lender = account_table.newEntry();
					entry_borrower.set("account", borrower);
					entry_borrower.set("balances", borrower_balances - amount);
					account_table.insert(borrower, entry_borrower);
					entry_lender.set("account", lender);
					entry_lender.set("balances", lender_balances + amount);
					account_table.insert(lender, entry_lender);
					ret_code = 0;
				}
			}
		} else {
			ret_code = -1;
		}
		emit remove_bill_event(ret_code);
		return ret_code;
	}
}

