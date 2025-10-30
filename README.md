# Outlaw Economy

Plugin Paper/Spigot pour offrir un système d'économie centralisé avec boutiques configurables, PNJ marchands et échanges sécurisés entre joueurs.

## Fonctionnalités
- Système d'argent persistant avec solde de départ configurable et affichage en sidebar.
- Commandes `/balance`, `/pay`, `/trade`, `/shop` adaptées aux joueurs comme aux administrateurs.
- Boutiques PNJ ou menus virtuels basés sur des templates configurables (`shop-templates.yml`).
- Achat/vente d'items avec prix, quantités (stack ou non) et valeur de reprise personnalisables.
- Système d'échange joueur à joueur incluant l'argent de l'économie.
- API publique pour que vos autres plugins (Clans, territoires, etc.) puissent manipuler l'argent.

## Installation
1. Compiler le plugin : `mvn package` (Java 17+ requis).
2. Déposer le fichier `target/outlaw-economy-1.0.0-SNAPSHOT.jar` dans le dossier `plugins` de votre serveur Paper/Spigot 1.20.4.
3. Démarrer le serveur afin de générer les fichiers de configuration (`config.yml`, `balances.yml`, `shops.yml`, `shop-templates.yml`).

## Commandes
| Commande | Description |
| --- | --- |
| `/balance` | Affiche le solde du joueur. |
| `/pay <joueur> <montant>` | Transfert d'argent entre joueurs. |
| `/trade <joueur>` | Envoie une demande d'échange. Sous-commandes : `accept`, `deny`, `cancel`. |
| `/shop open <template>` | Ouvre une boutique sans passer par un PNJ (joueurs, nécessite `outlaweco.use`). |
| `/shop templates` | Liste les boutiques disponibles et leur nom d'affichage. |
| `/shop create <template>` | Crée une boutique PNJ sur votre position (admin). |
| `/shop remove` | Supprime la boutique PNJ ciblée (admin). |
| `/shop list [templates]` | Liste les boutiques placées ou les templates (admin). |
| `/shop add itemshop <template> <item> <quantité> <prixAchat> [prixVente]` | Ajoute un item à un template (admin). |
| `/shop removeitem <template> <item>` | Retire un item d'un template (admin). |
| `/shop reloadtemplates` | Recharge les templates depuis `shop-templates.yml` (admin). |

## Permissions (LuckPerms, etc.)
| Permission | Description | Défaut |
| --- | --- | --- |
| `outlaweco.use` | Ouvrir les boutiques (PNJ ou commande) et interagir avec les menus. | `true` |
| `outlawecoadmin.use` | Gérer les templates et les PNJ via `/shop`. | `op` |

## Configuration (`config.yml`)
```yaml
economy:
  starting-balance: 100.0   # argent initial pour les nouveaux joueurs
  currency-name: "OutlawCoin"
trade:
  request-timeout: 30       # durée (secondes) des demandes d'échange
```

## Configuration des boutiques (`shop-templates.yml`)
Chaque entrée dans `templates:` correspond à une boutique. Exemple :
```yaml
templates:
  decoration:
    display-name: "&eBoutique Décoration"
    items:
      - material: GLOWSTONE
        quantity: 16          # nombre d'items donnés/retirés à chaque transaction
        buy-price: 120        # prix d'achat pour le joueur
        sell-price: 72        # prix de rachat par la boutique
      - material: TORCH
        stack: true           # équivalent à quantity: 64 si non défini
        buy-price: 12
```
- `material` doit correspondre à un `Material` Bukkit valide.
- `quantity` définit la taille du lot (mettez 64 pour un stack complet). `stack: true` permet d'utiliser la taille maximale du matériau si `quantity` n'est pas précisé.
- `buy-price` et `sell-price` peuvent être identiques ou différents. Si `sell-price` est omis, la valeur de `buy-price` est utilisée.
- Vous pouvez modifier ce fichier à la main puis exécuter `/shop reloadtemplates` en jeu.
- Les commandes `/shop add itemshop` et `/shop removeitem` mettent à jour le fichier automatiquement.

## Création et gestion des boutiques PNJ
- Placez-vous à l'endroit souhaité et exécutez `/shop create <template>` (ex. `tools`, `blocks`, `food` par défaut).
- Les joueurs avec la permission `outlaweco.use` peuvent cliquer sur le PNJ pour ouvrir le menu ou utiliser `/shop open <template>`.
- Les boutiques sont sauvegardées dans `plugins/OutlawEconomy/shops.yml`. Pour retirer un PNJ, regardez-le et utilisez `/shop remove`.
- `/shop list` affiche les PNJ enregistrés avec leur localisation.

## Système d'échange
- `/trade <joueur>` envoie une demande, qui expire après la durée définie dans la config.
- Lorsque l'échange est accepté, une interface s'ouvre avec deux colonnes : chaque joueur dépose ses items.
- Les boutons en bas permettent de proposer de l'argent (+1, +10, +100) et de confirmer l'échange.
- Si les deux joueurs confirment et possèdent les fonds nécessaires, l'échange est validé.

## API d'économie pour vos plugins
Deux approches sont possibles pour interagir avec l'économie depuis un autre plugin.

### 1. Utiliser l'API statique
```java
import com.outlaweco.api.EconomyAPI;

UUID joueur = ...;
EconomyAPI.deposit(joueur, 250.0);          // ajoute 250 OutlawCoin
boolean ok = EconomyAPI.withdraw(joueur, 50.0); // retire 50 (false si solde insuffisant)
double balance = EconomyAPI.getBalance(joueur);
```

### 2. Via le ServicesManager de Bukkit
```java
import com.outlaweco.api.EconomyService;
import org.bukkit.Bukkit;

EconomyService economy = Bukkit.getServicesManager().load(EconomyService.class);
if (economy != null) {
    UUID joueur = ...;
    if (economy.has(joueur, 500.0)) {
        economy.withdraw(joueur, 500.0);
        // ... effectuer l'achat d'un territoire, etc.
    }
}
```

L'interface `EconomyService` expose les méthodes suivantes :
- `double getBalance(UUID joueur)`
- `void setBalance(UUID joueur, double montant)`
- `void deposit(UUID joueur, double montant)`
- `boolean withdraw(UUID joueur, double montant)`
- `boolean has(UUID joueur, double montant)`

Assurez-vous que votre plugin déclare une dépendance vers OutlawEconomy (via `plugin.yml` ou `softdepend`) pour être chargé après celui-ci.

## Persistance des données
- `balances.yml` stocke les soldes des joueurs.
- `shops.yml` enregistre l'emplacement et le template associé à chaque PNJ.
- `shop-templates.yml` contient la configuration des boutiques (items, prix, quantités).

## Développement
- Langage : Java 17
- Build : Maven
- API serveur : Paper 1.20.4
