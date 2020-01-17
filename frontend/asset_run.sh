#!/bin/bash 

function usage() 
{
    echo " Usage : "
    echo "bash asset_run.sh deploy"
    echo "bash asset_run.sh show_balances    account"
    echo "bash asset_run.sh register_account account amount"
    echo "bash asset_run.sh show_bill        lender  borrower"
    echo "bash asset_run.sh create_bill      lender  borrower amount"
    echo "bash asset_run.sh remove_bill      lender  borrower"
    exit 0
}

    case $1 in
    deploy)
            [ $# -lt 1 ] && { usage; }
            ;;
    show_balances)
            [ $# -lt 2 ] && { usage; }
            ;;
    register_account)
            [ $# -lt 3 ] && { usage; }
            ;;
    show_bill)
            [ $# -lt 3 ] && { usage; }
            ;;
    create_bill)
            [ $# -lt 4 ] && { usage; }
            ;;
    remove_bill)
            [ $# -lt 3 ] && { usage; }
            ;;            
    *)
        usage
            ;;
    esac

    java -Djdk.tls.namedGroups="secp256k1" -cp 'apps/*:conf/:lib/*' org.fisco.bcos.asset.client.AssetClient $@
