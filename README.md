# SUSE / Rancher — labo public du projet fil rouge

Ce dépôt contient les fichiers nécessaires au projet du Jour 5 : quatre petites applications indépendantes, leurs Dockerfiles, une configuration Docker Compose et les manifestes pour les déployer sur Kubernetes avec RKE2.

Il se clone sans compte GitHub et sans accès aux supports de formation. Les Dockerfiles et manifestes sont déjà fournis : le travail consiste à les lire, expliquer leurs choix, les adapter si nécessaire, puis vérifier chaque étape.

## 1. Récupérer les fichiers

Dans un terminal sur la VM du labo :

```bash
git clone https://github.com/JSurquin/suz-rancher-lab.git
cd suz-rancher-lab
ls lab/apps/
```

Toutes les commandes ci-dessous partent de cette racine `suz-rancher-lab/`. Gardez ce terminal ; les fichiers Compose se trouvent sous `lab/`.

## 2. Comprendre les quatre applications

- `lab/apps/nextjs-demo/` : page Next.js, port du conteneur **3000**, port Compose **3000**, NodePort **30000**.
- `lab/apps/java-demo/` : serveur HTTP Java 21, port du conteneur **8080**, port Compose **8085**, NodePort **30080**.
- `lab/apps/node-api/` : API Node.js/Express, port **3001**, NodePort **30001**.
- `lab/apps/python-api/` : API Python/Flask, port **5000**, NodePort **30500**.

Ces quatre services sont indépendants. La page Next.js n’appelle pas les trois API : il faut tester les quatre applications séparément.

Le namespace Kubernetes utilisé est **`suz-rancher-lab`**. Les noms des images locales sont `suz-rancher/nextjs-demo:1.0`, `suz-rancher/java-demo:1.0`, `suz-rancher/node-api:1.0` et `suz-rancher/python-api:1.0` ; elles sont construites sur votre machine, pas téléchargées depuis un registre public.

## 3. Construire et tester avec Compose

Prérequis : Git, Docker Engine opérationnel et Docker Compose. Les ports 3000, 8085, 3001 et 5000 doivent être disponibles. Docker construit les applications dans les images : Java, Node.js et Python n’ont pas besoin d’être installés sur la VM.

```bash
docker compose -f lab/docker-compose.yml up -d --build
docker compose -f lab/docker-compose.yml ps
curl --fail http://localhost:3000
curl --fail http://localhost:8085
curl --fail http://localhost:3001
curl --fail http://localhost:5000
```

Si une application ne répond pas, consultez ses logs :

```bash
docker compose -f lab/docker-compose.yml logs --tail=50
```

Pour suivre les logs en direct, ajoutez `-f`. `Ctrl-C` arrête leur affichage, pas les conteneurs.

## 4. Préparer le passage à RKE2

Utilisez le cluster **RKE2 déjà installé pendant la formation** sur SLES ou Leap. Le repère du parcours est RKE2 **v1.35.8+rke2r1** et Rancher **2.15.2**. Ce dépôt n’installe ni RKE2 ni Rancher.

Le parcours prévoit 4 vCPU et 8 Go de RAM pour le nœud ; prévoyez 12 Go si Rancher partage la VM. Ce sont des repères de labo. Vérifiez aussi l’espace disque et la compatibilité OS/version avant installation.

Traefik doit déjà être installé sur le cluster et gérer la classe Ingress **`traefik`**. Sur le serveur RKE2, vérifiez :

```bash
kubectl get nodes -o wide
kubectl get ingressclass traefik
kubectl -n kube-system rollout status daemonset/rke2-traefik --timeout=180s
```

kind et RKE2 sont deux clusters distincts. Les ressources d’un ancien cluster kind ne sont pas transférées automatiquement.

## 5. Donner les images au moteur de RKE2

Docker construit les images ; RKE2 utilise **containerd**. Chaque nœud qui peut accueillir les Pods doit disposer des quatre images.

### Docker et RKE2 sur la même VM

Depuis la racine de ce dépôt, sur le serveur RKE2 :

```bash
docker compose -f lab/docker-compose.yml build
docker save \
  suz-rancher/nextjs-demo:1.0 \
  suz-rancher/java-demo:1.0 \
  suz-rancher/node-api:1.0 \
  suz-rancher/python-api:1.0 \
  | sudo /var/lib/rancher/rke2/bin/ctr \
      --address /run/k3s/containerd/containerd.sock \
      --namespace k8s.io images import -
```

`/run/k3s/containerd/containerd.sock` est bien le chemin de socket utilisé ici par RKE2 ; il ne demande pas d’installer k3s. `k8s.io` est un espace interne de containerd, distinct du namespace Kubernetes `suz-rancher-lab`.

### Autre machine ou plusieurs nœuds

Sur la machine où les images ont été construites :

```bash
docker save -o /tmp/lab-images.tar \
  suz-rancher/nextjs-demo:1.0 \
  suz-rancher/java-demo:1.0 \
  suz-rancher/node-api:1.0 \
  suz-rancher/python-api:1.0
scp /tmp/lab-images.tar COMPTE@ADRESSE_NOEUD:/tmp/lab-images.tar
```

Remplacez `COMPTE` et `ADRESSE_NOEUD` avant la copie. Sur **chaque nœud RKE2 susceptible d’accueillir les Pods**, importez l’archive :

```bash
sudo /var/lib/rancher/rke2/bin/ctr \
  --address /run/k3s/containerd/containerd.sock \
  --namespace k8s.io images import /tmp/lab-images.tar
```

La machine de construction et les nœuds doivent avoir une architecture compatible (amd64 ou arm64). Si vous utilisez un registre à la place, poussez les quatre images, adaptez leurs noms dans les Deployments et vérifiez l’accès au registre depuis tous les nœuds. `IfNotPresent` ne distribue pas les images entre les nœuds.

## 6. Déployer sur RKE2

Vérifiez que kubectl vise le bon cluster, puis appliquez les manifestes depuis la racine du dépôt :

```bash
kubectl config current-context
kubectl apply -f lab/k8s/
kubectl -n suz-rancher-lab rollout status deployment/nextjs-demo --timeout=180s
kubectl -n suz-rancher-lab rollout status deployment/java-demo --timeout=180s
kubectl -n suz-rancher-lab rollout status deployment/node-api --timeout=180s
kubectl -n suz-rancher-lab rollout status deployment/python-api --timeout=180s
kubectl -n suz-rancher-lab get pods,services,ingress
```

Les fichiers `10-…` à `40-…` fournissent les Deployments et leurs Services internes. `50-nodeport-services.yaml` ajoute les quatre accès NodePort et `60-ingress.yaml` publie le front Next.js.

## 7. Tester les applications

Depuis une machine qui peut joindre le nœud, remplacez `IP_NOEUD` dans les quatre commandes suivantes :

```bash
curl --fail http://IP_NOEUD:30000
curl --fail http://IP_NOEUD:30080
curl --fail http://IP_NOEUD:30001
curl --fail http://IP_NOEUD:30500
```

Pour l’Ingress, ajoutez une ligne `IP_NOEUD app.lab.local` au fichier `hosts` de **la machine du navigateur**. Ouvrez ensuite **`http://app.lab.local`**. Ce manifeste configure HTTP ; il ne crée ni DNS public ni certificat HTTPS.

Pour tester sans modifier `hosts`, envoyez explicitement le nom HTTP attendu :

```bash
curl --fail -H 'Host: app.lab.local' http://IP_NOEUD/
```

Dans Rancher installé avec Helm sur ce RKE2, ouvrez le cluster **`local`**, puis sélectionnez le namespace **`suz-rancher-lab`**. Retrouvez les quatre Deployments, leurs Pods, les Services et l’Ingress.

## 8. Quand un résultat manque

- `ImagePullBackOff` : vérifiez que chaque nœud dispose des images sous les noms exacts des manifestes.
- Connexion refusée ou délai dépassé : vérifiez l’adresse du nœud, les ports, les routes et le filtrage réseau.
- 404 de Traefik : vérifiez `app.lab.local`, le chemin et la classe de l’Ingress.
- 502/503 : vérifiez le Service cible et ses Pods prêts, puis les événements et les logs.

```bash
kubectl -n suz-rancher-lab get events --sort-by=.metadata.creationTimestamp
kubectl -n suz-rancher-lab describe deployment nextjs-demo
kubectl -n suz-rancher-lab logs deployment/nextjs-demo --tail=50
```

## 9. Arrêter ou nettoyer

Arrêter les conteneurs Compose de ce projet :

```bash
docker compose -f lab/docker-compose.yml down
```

Quand vous avez terminé le labo Kubernetes, supprimer uniquement ses ressources :

```bash
kubectl delete -f lab/k8s/
```

Cette dernière commande supprime aussi le namespace `suz-rancher-lab` et toutes les ressources qui y auraient été ajoutées. Elle ne désinstalle ni RKE2 ni Rancher. Conservez vos fichiers et les ressources utiles avant de nettoyer.

## Structure du dépôt

```text
README.md
lab/
├── apps/                 # Code source et Dockerfiles des quatre applications
├── docker-compose.yml    # Construction des images et essai local
└── k8s/                  # Namespace, Deployments, Services, NodePorts et Ingress
```

Les scripts d’installation du sandbox Ubuntu/k3s, les supports de formation et les configurations du formateur ne sont pas nécessaires pour utiliser ce dépôt.
